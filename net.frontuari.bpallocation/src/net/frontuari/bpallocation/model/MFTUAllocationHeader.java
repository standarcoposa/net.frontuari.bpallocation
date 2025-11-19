package net.frontuari.bpallocation.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MPeriod;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.Query;
import org.compiere.model.X_C_Invoice;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;

public class MFTUAllocationHeader extends MAllocationHdr {

	private static final long serialVersionUID = -4974958234117252364L;

	public MFTUAllocationHeader(Properties ctx, String C_AllocationHdr_UU, String trxName) {
		super(ctx, C_AllocationHdr_UU, trxName);
	}

	public MFTUAllocationHeader(Properties ctx, int C_AllocationHdr_ID, String trxName) {
		super(ctx, C_AllocationHdr_ID, trxName);
	}

	public MFTUAllocationHeader(Properties ctx, boolean IsManual, Timestamp DateTrx, int C_Currency_ID,
			String description, String trxName) {
		super(ctx, IsManual, DateTrx, C_Currency_ID, description, trxName);
	}

	public MFTUAllocationHeader(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	/**	Lines						*/
	private MFTUAllocationLine[]	m_lines = null;
	
	/**
	 * 	Get Allocation Lines
	 *	@param requery if true requery
	 *	@return allocation lines
	 */
	public MFTUAllocationLine[] getLines (boolean requery)
	{
		if (m_lines != null && m_lines.length != 0 && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		//
		String sql = "SELECT * FROM C_AllocationLine WHERE C_AllocationHdr_ID=?";
		ArrayList<MFTUAllocationLine> list = new ArrayList<MFTUAllocationLine>();
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, get_TrxName());
			pstmt.setInt (1, getC_AllocationHdr_ID());
			rs = pstmt.executeQuery ();
			while (rs.next ())
			{
				MFTUAllocationLine line = new MFTUAllocationLine(getCtx(), rs, get_TrxName());
				line.setParent(this);
				list.add (line);
			}
		} 
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		//
		m_lines = new MFTUAllocationLine[list.size ()];
		list.toArray (m_lines);
		return m_lines;
	}	//	getLines
	
	// Goodwill.co.id
	/** Reversal Flag		*/
	private boolean m_reversal = false;
	
	/**
	 * 	Set Reversal
	 *	@param reversal reversal
	 */
	private void setReversal(boolean reversal)
	{
		m_reversal = reversal;
	}	//	setReversal
	
	/**
	 * 	Is Reversal
	 *	@return true if it is a reversal document
	 */
	private boolean isReversal()
	{
		return m_reversal;
	}

	/**
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	@Override
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;
	
	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid) 
	 */
	@Override
	public String prepareIt()
	{
		if (log.isLoggable(Level.INFO)) log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Std Period open?
		MPeriod.testPeriodOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_PaymentAllocation, getAD_Org_ID());
		getLines(true);
		if (m_lines.length == 0)
		{
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}
		
		// Stop the Document Workflow if invoice to allocate is as paid
		if (!isReversal()) {
			for (MFTUAllocationLine line :m_lines)
			{	
				if (line.getC_Invoice_ID() != 0)
				{
					StringBuilder whereClause = new StringBuilder(I_C_Invoice.COLUMNNAME_C_Invoice_ID).append("=? AND ") 
									   .append(I_C_Invoice.COLUMNNAME_IsPaid).append("=? AND ")
									   .append(I_C_Invoice.COLUMNNAME_DocStatus).append(" NOT IN (?,?)");
					boolean InvoiceIsPaid = new Query(getCtx(), I_C_Invoice.Table_Name, whereClause.toString(), get_TrxName())
					.setClient_ID()
					.setParameters(line.getC_Invoice_ID(), "Y", X_C_Invoice.DOCSTATUS_Voided, X_C_Invoice.DOCSTATUS_Reversed)
					.match();
					if (InvoiceIsPaid && line.getAmount().signum() > 0)
						throw new  AdempiereException("@ValidationError@ @C_Invoice_ID@ @IsPaid@");
				}
			}	
		}
		
		//	Add up Amounts & validate
		BigDecimal approval = Env.ZERO;
		for (int i = 0; i < m_lines.length; i++)
		{
			MFTUAllocationLine line = m_lines[i];
			approval = approval.add(line.getWriteOffAmt()).add(line.getDiscountAmt());
			//	Make sure there is BP
			if (line.getC_BPartner_ID() == 0)
			{
				m_processMsg = "No Business Partner";
				return DocAction.STATUS_Invalid;
			}

			// IDEMPIERE-1850 - validate date against related docs
			if (line.getC_Invoice_ID() > 0) {
				//	Added By Jorge Colmenarez, 2020-10-30 19:53, Trunc DateAcct because add Date+Hour on DateAcct from Invoice
				Timestamp DateInv = TimeUtil.trunc(line.getC_Invoice().getDateAcct(), TimeUtil.TRUNC_DAY);
				//if (line.getC_Invoice().getDateAcct().after(getDateAcct())) {
				if (DateInv.after(getDateAcct())) {
				//	End Jorge Colmenarez
					m_processMsg = "Wrong allocation date";
					return DocAction.STATUS_Invalid;
				}
			}
			if (line.getC_Payment_ID() > 0) {
				//	Added By Jorge Colmenarez, 2020-10-30 19:53, Trunc DateAcct because add Date+Hour on DateAcct from Payment
				Timestamp DatePay = TimeUtil.trunc(line.getC_Payment().getDateAcct(), TimeUtil.TRUNC_DAY);
				//if (line.getC_Payment().getDateAcct().after(getDateAcct())) {
				if (DatePay.after(getDateAcct())) {
				//	End Jorge Colmenarez
					m_processMsg = "Wrong allocation date";
					return DocAction.STATUS_Invalid;
				}
			}
		}
		setApprovalAmt(approval);
		//
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	/**
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	@Override
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		if (log.isLoggable(Level.INFO)) log.info(toString());

		//	Link
		getLines(false);
		if(!updateBP())
			return DocAction.STATUS_Invalid;
		
		for (int i = 0; i < m_lines.length; i++)
		{
			MFTUAllocationLine line = m_lines[i];
			line.processIt(isReversal());
		}		

		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}

		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	/**
	 * Update open balance of BP 
	 * @return true if updated successfully
	 */
	private boolean updateBP()
	{
		List<Integer> bps = new ArrayList<Integer>();
		getLines(false);
		for (MFTUAllocationLine line : m_lines) {
			int C_BPartner_ID = line.getC_BPartner_ID();
			if (! bps.contains(C_BPartner_ID)) {
				bps.add(C_BPartner_ID);
				MBPartner bpartner = new MBPartner(Env.getCtx(), C_BPartner_ID, get_TrxName());
				bpartner.setTotalOpenBalance();
				bpartner.saveEx();
			}
		} // for all lines
		return true;
	}	//	updateBP
	
	/**
	 * 	Get Process Message
	 *	@return clear text error message
	 */
	@Override
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg

}
