package net.frontuari.bpallocation.webui.apps.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MRole;
import org.compiere.model.MSysConfig;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Util;

import net.frontuari.bpallocation.base.CustomForm;
import net.frontuari.bpallocation.model.MFTUPayment;

public class FTUVAllocation extends CustomForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4790962689984184556L;

	public DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);

	/**	Logger			*/
	public static CLogger log = CLogger.getCLogger(FTUVAllocation.class);

	private boolean     m_calculating = false;
	public int         	m_C_Currency_ID = 0;
	public int         m_C_Charge_ID = 0;
	public int         m_C_DocType_ID = 0;
	public int         	m_C_BPartner_ID = 0;
	public int         	m_C_BPartner2_ID = 0;
	private int         m_noInvoices = 0;
	private int         m_noPayments = 0;
	public BigDecimal	totalInv = Env.ZERO;
	public BigDecimal 	totalPay = Env.ZERO;
	public BigDecimal	totalDiff = Env.ZERO;
	
	public Timestamp allocDate = null;

	//  Index	changed if multi-currency
	private int         i_payment = 7;
	//
	private int         i_open = 6;
	private int         i_discount = 7;
	private int         i_writeOff = 8; 
	private int         i_applied = 9;
	private int 		i_overUnder = 10;
//	private int			i_multiplier = 10;
	
	public int         	m_AD_Org_ID = 0;
	
	public int         	m_AD_OrgTarget_ID = 0;

	private ArrayList<Integer>	m_bpartnerCheck = new ArrayList<Integer>(); 
	//	Added By Jorge Colmenarez, 2024-01-18 10:57
	//	Create local variables for filter by DocType/Role Access
	public boolean filterbyDocType = false;
	public int         	m_AD_Role_ID = 0;
	//	Added By Jorge Colmenarez, 2024-03-11 15:15
	//	Add Activity and Cost Center
	public int         m_C_Activity_ID = 0;
	public int         m_User1_ID = 0;
	//	Create local variables for always update allocation date
	public boolean alwaysUpdateAllocationDate = false;
	//	End Jorge Colmenarez

	public void dynInit() throws Exception
	{
		m_C_Currency_ID = Env.getContextAsInt(Env.getCtx(), "$C_Currency_ID");   //  default
		//
		if (log.isLoggable(Level.INFO)) log.info("Currency=" + m_C_Currency_ID);
		
		m_AD_Org_ID = Env.getAD_Org_ID(Env.getCtx());
		m_AD_OrgTarget_ID = Env.getAD_Org_ID(Env.getCtx());
		m_C_DocType_ID= MDocType.getDocType("CMA");
		//	Added by Jorge Colmenarez, 2024-01-15 18:02
		//	get Sysconfig value Allocation filter by Document Type
		filterbyDocType = MSysConfig.getBooleanValue("ALLOCATION_FILTER_BY_DOCTYPE", false, Env.getContextAsInt(Env.getCtx(), "#AD_Client_ID"), Env.getContextAsInt(Env.getCtx(), "#AD_Org_ID"));
		//	Update Always AllocationDate
		alwaysUpdateAllocationDate = MSysConfig.getBooleanValue("ALLOCATION_ALWAYS_UPDATE_ALLOCATIONDATE", false, Env.getContextAsInt(Env.getCtx(), "#AD_Client_ID"), Env.getContextAsInt(Env.getCtx(), "#AD_Org_ID"));
		m_AD_Role_ID = Env.getContextAsInt(Env.getCtx(), "#AD_Role_ID");   //  default
		//	End Jorge Colmenarez
	}
	
	/**
	 *  Load Business Partner Info
	 *  - Payments
	 *  - Invoices
	 */
	public void checkBPartner()
	{		
		if (log.isLoggable(Level.CONFIG)) log.config("BPartner=" + m_C_BPartner_ID + ", Cur=" + m_C_Currency_ID);
		//  Need to have both values
		if (m_C_BPartner_ID == 0 || m_C_Currency_ID == 0)
			return;

		//	Async BPartner Test
		Integer key = Integer.valueOf(m_C_BPartner_ID);
		if (!m_bpartnerCheck.contains(key))
		{
			new Thread()
			{
				public void run()
				{
					MFTUPayment.setIsAllocated (Env.getCtx(), m_C_BPartner_ID, null);
					MInvoice.setIsPaid (Env.getCtx(), m_C_BPartner_ID, null);
				}
			}.start();
			m_bpartnerCheck.add(key);
		}
	}
	
	/**
	 *  Load Business Partner Info
	 *  - Invoices
	 *  - Payments
	 */
	public void checkBPartner2()
	{		
		if (log.isLoggable(Level.CONFIG)) log.config("BPartner=" + m_C_BPartner2_ID + ", Cur=" + m_C_Currency_ID);
		//  Need to have both values
		if (m_C_BPartner2_ID == 0 || m_C_Currency_ID == 0)
			return;

		//	Async BPartner Test
		Integer key = new Integer(m_C_BPartner2_ID);
		if (!m_bpartnerCheck.contains(key))
		{
			new Thread()
			{
				public void run()
				{
					MFTUPayment.setIsAllocated (Env.getCtx(), m_C_BPartner2_ID, null);
					MInvoice.setIsPaid (Env.getCtx(), m_C_BPartner2_ID, null);
				}
			}.start();
			m_bpartnerCheck.add(key);
		}
	}
	
	public Vector<Vector<Object>> getPaymentData(boolean isMultiCurrency, Object date, IMiniTable paymentTable, boolean isDocTypeFilter, int docTypePayment)
	{		
		/********************************
		 *  Load unallocated Payments
		 *      1-TrxDate, 2-DocumentNo, (3-Currency, 4-PayAmt,)
		 *      5-ConvAmt, 6-ConvOpen, 7-Allocated
		 */
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql = new StringBuilder("SELECT p.DateTrx,p.DocumentNo,p.C_Payment_ID,"  //  1..3
			+ "c.ISO_Code,p.PayAmt,"                            //  4..5
			+ "currencyConvertPayment(p.C_Payment_ID,?,p.PayAmt,p.DateTrx),"//  6   #1, #2
			+ "PaymentAvailableConverted(p.C_Payment_ID,?),"  //  7   #3, #4
			+ "p.MultiplierAP "
			+ ",bp.Name " // 9
			+ ",p.DateAcct "	//	10	//	Added by Jorge Colmenarez, 2022-01-05 16:39 RQ #0000225
			+ ",p.Description " // 11
			+ "FROM C_Payment_v p"		//	Corrected for AP/AR 
			+ " INNER JOIN AD_Org bp ON (p.AD_Org_ID = bp.AD_Org_ID) "
			+ " INNER JOIN C_Currency c ON (p.C_Currency_ID=c.C_Currency_ID) "
			+ "WHERE p.IsAllocated='N' AND p.Processed='Y'"
			+ " AND p.C_Charge_ID IS NULL"		//	Prepayments OK
			+ " AND p.C_BPartner_ID = ? ");                   		//      #5,#6
		if (!isMultiCurrency)
			sql.append(" AND p.C_Currency_ID=?");				//      #7
		if (m_AD_Org_ID != 0 )
			sql.append(" AND p.AD_Org_ID=" + m_AD_Org_ID);
		
		//	Added by Jorge Colmenarez, 2024-01-018 10:53
		//	Filter by DocType Selected or Role Access
		if(filterbyDocType) {
			if(isDocTypeFilter && docTypePayment > 0) {
				sql.append(" AND p.C_DocType_ID = "+docTypePayment+" ");
			}else {
				sql.append(" AND p.C_DocType_ID IN (select distinct C_DocType_ID from AD_Document_Action_Access daa where daa.AD_Role_ID IN ((select Included_Role_ID from AD_Role_Included ri where ri.AD_Role_ID="+m_AD_Role_ID+" union all select "+m_AD_Role_ID+"))) ");
			}
		}
		//	End Jorge Colmenarez
		sql.append(" ORDER BY p.DateTrx,p.DocumentNo");
		
		// role security
		sql = new StringBuilder( MRole.getDefault(Env.getCtx(), false).addAccessSQL( sql.toString(), "p", MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO ) );
		
		if (log.isLoggable(Level.FINE)) log.fine("PaySQL=" + sql.toString());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, m_C_Currency_ID);
			pstmt.setInt(2, m_C_Currency_ID);
			pstmt.setInt(3, m_C_BPartner_ID);
			if (!isMultiCurrency)
				pstmt.setInt(4, m_C_Currency_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(Boolean.FALSE);       //  0-Selection
				line.add(rs.getTimestamp(1));       //  1-TrxDate
				KeyNamePair pp = new KeyNamePair(rs.getInt(3), rs.getString(2));
				line.add(pp);                       //  2-DocumentNo
				line.add(rs.getString(9));			//	3-BPartner
				if (isMultiCurrency)
				{
					line.add(rs.getString(4));      //  4-Currency
					line.add(rs.getBigDecimal(5));  //  5-PayAmt
				}
				line.add(rs.getBigDecimal(6));      //  4/6-ConvAmt
				BigDecimal available = rs.getBigDecimal(7);
				if (available == null || available.signum() == 0)	//	nothing available
					continue;
				line.add(available);				//  5/7-ConvOpen/Available
				line.add(Env.ZERO);					//  6/8-Payment
//				line.add(rs.getBigDecimal(8));		//  7/9-Multiplier
				//	Added by Jorge Colmenarez, 2022-01-05 16:41 RQ #0000225 
				line.add(rs.getTimestamp(10));		//	9-DateAcct
				line.add(rs.getString(11));
				//
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
		}
		
		return data;
	}
	
	public Vector<String> getPaymentColumnNames(boolean isMultiCurrency)
	{	
		//  Header Info
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
		columnNames.add(Msg.translate(Env.getCtx(), "Date"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "DocumentNo")));
		columnNames.add(Msg.translate(Env.getCtx(), "AD_Org_ID"));
		if (isMultiCurrency)
		{
			columnNames.add(Msg.getMsg(Env.getCtx(), "TrxCurrency"));
			columnNames.add(Msg.translate(Env.getCtx(), "Amount"));
		}
		columnNames.add(Msg.getMsg(Env.getCtx(), "ConvertedAmount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OpenAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "AppliedAmt"));
//		columnNames.add(" ");	//	Multiplier
		//	Added by Jorge Colmenarez, 2022-01-05 16:44 RQ #0000225
		columnNames.add(Msg.translate(Env.getCtx(), "DateAcct"));
		columnNames.add(Msg.translate(Env.getCtx(), "Description"));
		
		return columnNames;
	}
	
	public void setPaymentColumnClass(IMiniTable paymentTable, boolean isMultiCurrency)
	{
		int i = 0;
		paymentTable.setColumnClass(i++, Boolean.class, false);         //  0-Selection
		paymentTable.setColumnClass(i++, Timestamp.class, true);        //  1-TrxDate
		paymentTable.setColumnClass(i++, String.class, true);           //  2-Value
		paymentTable.setColumnClass(i++, String.class, true);       	//  3-BPartner
		if (isMultiCurrency)
		{
			paymentTable.setColumnClass(i++, String.class, true);       //  4-Currency
			paymentTable.setColumnClass(i++, BigDecimal.class, true);   //  5-PayAmt
		}
		paymentTable.setColumnClass(i++, BigDecimal.class, true);       //  6-ConvAmt
		paymentTable.setColumnClass(i++, BigDecimal.class, true);       //  7-ConvOpen
		paymentTable.setColumnClass(i++, BigDecimal.class, false);      //  8-Allocated
//		paymentTable.setColumnClass(i++, BigDecimal.class, true);      	//  9-Multiplier

		//	Added by Jorge Colmenarez, 2022-01-05 16:44 RQ #0000225
		paymentTable.setColumnClass(i++, Timestamp.class, true);        //  9-DateAcct
		paymentTable.setColumnClass(i++, String.class, true); 			// 10 - Description

		//
		i_payment = isMultiCurrency ? 8 : 6;
		

		//  Table UI
		paymentTable.autoSize();
	}
	
	public Vector<Vector<Object>> getInvoiceData(boolean isMultiCurrency, Object date, IMiniTable invoiceTable, boolean isDocTypeFilter, int docTypeInvoice)
	{
		/********************************
		 *  Load unpaid Invoices
		 *      1-TrxDate, 2-Value, (3-Currency, 4-InvAmt,)
		 *      5-ConvAmt, 6-ConvOpen, 7-ConvDisc, 8-WriteOff, 9-Applied
		 * 
		 SELECT i.DateInvoiced,i.DocumentNo,i.C_Invoice_ID,c.ISO_Code,
		 i.GrandTotal*i.MultiplierAP "GrandTotal", 
		 currencyConvert(i.GrandTotal*i.MultiplierAP,i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID) "GrandTotal $", 
		 invoiceOpen(C_Invoice_ID,C_InvoicePaySchedule_ID) "Open",
		 currencyConvert(invoiceOpen(C_Invoice_ID,C_InvoicePaySchedule_ID),i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)*i.MultiplierAP "Open $", 
		 invoiceDiscount(i.C_Invoice_ID,SysDate,C_InvoicePaySchedule_ID) "Discount",
		 currencyConvert(invoiceDiscount(i.C_Invoice_ID,SysDate,C_InvoicePaySchedule_ID),i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)*i.Multiplier*i.MultiplierAP "Discount $",
		 i.MultiplierAP, i.Multiplier 
		 FROM C_Invoice_v i INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID) 
		 WHERE -- i.IsPaid='N' AND i.Processed='Y' AND i.C_BPartner_ID=1000001
		 */
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql = new StringBuilder("SELECT i.DateInvoiced,i.DocumentNo,i.C_Invoice_ID," //  1..3
			+ "c.ISO_Code,i.GrandTotal*i.MultiplierAP, "                            //  4..5    Orig Currency
			+ "currencyConvertInvoice(i.C_Invoice_ID,?,i.GrandTotal*i.MultiplierAP,i.DateInvoiced), " //  6   #1  Converted, #2 Date
			+ "invoiceOpenConverted(C_Invoice_ID,?::numeric)*i.MultiplierAP, "  //  7   #3, #4  Converted Open
			+ "currencyConvertInvoice(i.C_Invoice_ID,?,invoiceDiscount(i.C_Invoice_ID,?,C_InvoicePaySchedule_ID),i.DateInvoiced)*i.Multiplier*i.MultiplierAP,"               //  #5, #6
			+ "i.MultiplierAP "
			+ ",bp.Name " //	10
			//	Added by Jorge Colmenarez, 2022-01-05 16:46 RQ #0000225
			+ ",i.DateAcct "	//	11
			+ ",i.Description " //12 Description
			+ "FROM C_Invoice_v i"		//  corrected for CM/Split 
			+ " INNER JOIN AD_Org bp ON (i.AD_Org_ID = bp.AD_Org_ID) "
			+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID) "
			+ "WHERE i.IsPaid='N' AND i.Processed='Y'"
			+ " AND i.C_BPartner_ID = ? ");                                            //  #7
		if (!isMultiCurrency)
			sql.append(" AND i.C_Currency_ID=?");                                   //  #8
		if (m_AD_OrgTarget_ID != 0 ) 
			sql.append(" AND i.AD_Org_ID=" + m_AD_OrgTarget_ID);
		
		//	Added by Jorge Colmenarez, 2024-01-018 10:53
		//	Filter by DocType Selected or Role Access
		if(filterbyDocType) {
			if(isDocTypeFilter && docTypeInvoice > 0) {
				sql.append(" AND i.C_DocType_ID = "+docTypeInvoice+" ");
			}else {
				sql.append(" AND i.C_DocType_ID IN (select distinct C_DocType_ID from AD_Document_Action_Access daa where daa.AD_Role_ID IN ((select Included_Role_ID from AD_Role_Included ri where ri.AD_Role_ID="+m_AD_Role_ID+" union all select "+m_AD_Role_ID+"))) ");
			}
		}
		//	End Jorge Colmenarez
		sql.append(" ORDER BY i.DateInvoiced, i.DocumentNo");
		if (log.isLoggable(Level.FINE)) log.fine("InvSQL=" + sql.toString());
		
		// role security
		sql = new StringBuilder( MRole.getDefault(Env.getCtx(), false).addAccessSQL( sql.toString(), "i", MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO ) );
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, m_C_Currency_ID);
			pstmt.setInt(2, m_C_Currency_ID);
			pstmt.setInt(3, m_C_Currency_ID);
			pstmt.setTimestamp(4, (Timestamp)date);
			pstmt.setInt(5, m_C_BPartner_ID);
			if (!isMultiCurrency)
				pstmt.setInt(6, m_C_Currency_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(new Boolean(false));       //  0-Selection
				line.add(rs.getTimestamp(1));       //  1-TrxDate
				KeyNamePair pp = new KeyNamePair(rs.getInt(3), rs.getString(2));
				line.add(pp);                       //  2-Value
				line.add(rs.getString(10));      	//  3-BPartner
				if (isMultiCurrency)
				{
					line.add(rs.getString(4));      //  4-Currency
					line.add(rs.getBigDecimal(5));  //  5-Orig Amount
				}
				line.add(rs.getBigDecimal(6));      //  4/6-ConvAmt
				BigDecimal open = rs.getBigDecimal(7);
				if (open == null)		//	no conversion rate
					open = Env.ZERO;
				line.add(open);      				//  5/7-ConvOpen
				BigDecimal discount = rs.getBigDecimal(8);
				if (discount == null)	//	no concersion rate
					discount = Env.ZERO;
				line.add(discount);					//  6/8-ConvAllowedDisc
				line.add(Env.ZERO);      			//  7/9-WriteOff
				line.add(Env.ZERO);					// 8/10-Applied
				line.add(open);				    //  9/11-OverUnder

//				line.add(rs.getBigDecimal(9));		//	8/10-Multiplier
				//	Add when open <> 0 (i.e. not if no conversion rate)
				//	Added by Jorge Colmenarez, 2022-01-05 16:46 RQ #0000225
				line.add(rs.getTimestamp(11));       //  1-DateAcct
				line.add(rs.getString(12));    //Description
				if (Env.ZERO.compareTo(open) != 0)
					data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
		}
		
		return data;
	}

	public Vector<String> getInvoiceColumnNames(boolean isMultiCurrency)
	{
		//  Header Info
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
		columnNames.add(Msg.translate(Env.getCtx(), "Date"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "DocumentNo")));
		columnNames.add(Msg.translate(Env.getCtx(), "AD_Org_ID"));
		if (isMultiCurrency)
		{
			columnNames.add(Msg.getMsg(Env.getCtx(), "TrxCurrency"));
			columnNames.add(Msg.translate(Env.getCtx(), "Amount"));
		}
		columnNames.add(Msg.getMsg(Env.getCtx(), "ConvertedAmount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OpenAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "Discount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "WriteOff"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "AppliedAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OverUnderAmt"));
//		columnNames.add(" ");	//	Multiplier
		//	Added by Jorge Colmenarez, 2022-01-05 16:47 RQ #0000225
		columnNames.add(Msg.translate(Env.getCtx(), "DateAcct"));
		columnNames.add(Msg.translate(Env.getCtx(), "Description"));
		
		return columnNames;
	}
	
	public void setInvoiceColumnClass(IMiniTable invoiceTable, boolean isMultiCurrency)
	{
		int i = 0;
		invoiceTable.setColumnClass(i++, Boolean.class, false);         //  0-Selection
		invoiceTable.setColumnClass(i++, Timestamp.class, true);        //  1-TrxDate
		invoiceTable.setColumnClass(i++, String.class, true);           //  2-Value
		invoiceTable.setColumnClass(i++, String.class, true);       	//  3-BPartner
		if (isMultiCurrency)
		{
			invoiceTable.setColumnClass(i++, String.class, true);       //  4-Currency
			invoiceTable.setColumnClass(i++, BigDecimal.class, true);   //  5-Amt
		}
		invoiceTable.setColumnClass(i++, BigDecimal.class, true);       //  6-ConvAmt
		invoiceTable.setColumnClass(i++, BigDecimal.class, true);       //  7-ConvAmt Open
		invoiceTable.setColumnClass(i++, BigDecimal.class, false);      //  8-Conv Discount
		invoiceTable.setColumnClass(i++, BigDecimal.class, false);      //  9-Conv WriteOff
		invoiceTable.setColumnClass(i++, BigDecimal.class, false);      //  10-Conv OverUnder
		invoiceTable.setColumnClass(i++, BigDecimal.class, true);		//	11-Conv Applied
//		invoiceTable.setColumnClass(i++, BigDecimal.class, true);      	//  10-Multiplier
		//	Added by Jorge Colmenarez, 2022-01-05 16:47 RQ #0000225
		invoiceTable.setColumnClass(i++, Timestamp.class, true);        //  11-DateAcct
		invoiceTable.setColumnClass(i++, String.class, true);
		//  Table UI
		invoiceTable.autoSize();
	}
	
	public void calculate(boolean isMultiCurrency)
	{
		i_open = isMultiCurrency ? 7 : 5;
		i_discount = isMultiCurrency ? 8 : 6;
		i_writeOff = isMultiCurrency ? 9 : 7;
		i_applied = isMultiCurrency ? 10 : 8;
		i_overUnder = isMultiCurrency ? 11 : 9;
//		i_multiplier = isMultiCurrency ? 10 : 8;
	}   //  loadBPartner
	
	public String writeOff(int row, int col, boolean isInvoice, IMiniTable payment, IMiniTable invoice, boolean isAutoWriteOff)
	{
		String msg = "";
		/**
		 *  Setting defaults
		 */
		if (m_calculating)  //  Avoid recursive calls
			return msg;
		m_calculating = true;
		
		if (log.isLoggable(Level.CONFIG)) log.config("Row=" + row 
			+ ", Col=" + col + ", InvoiceTable=" + isInvoice);
        
		//  Payments
		if (!isInvoice)
		{
			BigDecimal open = (BigDecimal)payment.getValueAt(row, i_open);
			BigDecimal applied = (BigDecimal)payment.getValueAt(row, i_payment);
			
			if (col == 0)
			{
				// selection of payment row
				if (((Boolean)payment.getValueAt(row, 0)).booleanValue())
				{
					applied = open;   //  Open Amount
					if (totalDiff.abs().compareTo(applied.abs()) < 0			// where less is available to allocate than open
							&& totalDiff.signum() == -applied.signum() )    	// and the available amount has the opposite sign
						applied = totalDiff.negate();						// reduce the amount applied to what's available
					
				}
				else    //  de-selected
					applied = Env.ZERO;
			}
			
			
			if (col == i_payment)
			{
				if (! MSysConfig.getBooleanValue(MSysConfig.ALLOW_APPLY_PAYMENT_TO_CREDITMEMO, false, Env.getAD_Client_ID(Env.getCtx())) 
						&& open.signum() > 0 && applied.signum() == -open.signum() )
					applied = applied.negate();
				if (! MSysConfig.getBooleanValue(MSysConfig.ALLOW_OVER_APPLIED_PAYMENT, false, Env.getAD_Client_ID(Env.getCtx())))
					if ( open.abs().compareTo( applied.abs() ) < 0 )
						applied = open;
			}
			
			payment.setValueAt(applied, row, i_payment);
		}

		//  Invoice
		else 
		{
			boolean selected = ((Boolean) invoice.getValueAt(row, 0)).booleanValue();
			BigDecimal open = (BigDecimal)invoice.getValueAt(row, i_open);
			BigDecimal discount = (BigDecimal)invoice.getValueAt(row, i_discount);
			BigDecimal applied = (BigDecimal)invoice.getValueAt(row, i_applied);
			BigDecimal writeOff = (BigDecimal) invoice.getValueAt(row, i_writeOff);
			BigDecimal overUnder = (BigDecimal) invoice.getValueAt(row, i_overUnder);
			int openSign = open.signum();
			
			if (col == 0)  //selection
			{
				//  selected - set applied amount
				if ( selected )
				{
					applied = open;    //  Open Amount
					applied = applied.subtract(discount);
					writeOff = Env.ZERO;  //  to be sure
					overUnder = Env.ZERO;
					totalDiff = Env.ZERO;

					if (totalDiff.abs().compareTo(applied.abs()) < 0			// where less is available to allocate than open
							&& totalDiff.signum() == applied.signum() )     	// and the available amount has the same sign
						applied = totalDiff;									// reduce the amount applied to what's available

					if ( isAutoWriteOff )
						writeOff = open.subtract(applied.add(discount));
					else
						overUnder = open.subtract(applied.add(discount));
				}
				else    //  de-selected
				{
					writeOff = Env.ZERO;
					applied = Env.ZERO;
					overUnder = Env.ZERO;
				}
			}
			
			// check entered values are sensible and possibly auto write-off
			if ( selected && col != 0 )
			{
				
				// values should have same sign as open except possibly over/under
				if ( discount.signum() == -openSign )
					discount = discount.negate();
				if ( writeOff.signum() == -openSign)
					writeOff = writeOff.negate();
				if ( applied.signum() == -openSign )
					applied = applied.negate();
				
				// discount and write-off must be less than open amount
				if ( discount.abs().compareTo(open.abs()) > 0)
					discount = open;
				if ( writeOff.abs().compareTo(open.abs()) > 0)
					writeOff = open;
				
				
				/*
				 * Two rules to maintain:
				 *
				 * 1) |writeOff + discount| < |open| 
				 * 2) discount + writeOff + overUnder + applied = 0
				 *
				 *   As only one column is edited at a time and the initial position was one of compliance
				 *   with the rules, we only need to redistribute the increase/decrease in the edited column to 
				 *   the others.
				*/
				BigDecimal newTotal = discount.add(writeOff).add(applied).add(overUnder);  // all have same sign
				BigDecimal difference = newTotal.subtract(open);
				
				// rule 2
				BigDecimal diffWOD = writeOff.add(discount).subtract(open);
										
				if ( diffWOD.signum() == open.signum() )  // writeOff and discount are too large
				{
					if ( col == i_discount )       // then edit writeoff
					{
						writeOff = writeOff.subtract(diffWOD);
					} 
					else                            // col = i_writeoff
					{
						discount = discount.subtract(diffWOD);
					}
					
					difference = difference.subtract(diffWOD);
				}
				
				// rule 1
				if ( col == i_applied )
					overUnder = overUnder.subtract(difference);
				else
					applied = applied.subtract(difference);
				
			}
			
			//	Warning if write Off > 30%
			if (isAutoWriteOff && writeOff.doubleValue()/open.doubleValue() > .30)
				msg = "AllocationWriteOffWarn";

			invoice.setValueAt(discount, row, i_discount);
			invoice.setValueAt(applied, row, i_applied);
			invoice.setValueAt(writeOff, row, i_writeOff);
			invoice.setValueAt(overUnder, row, i_overUnder);
		}

		m_calculating = false;
		
		return msg;
	}
	
	/**
	 *  Calculate Allocation info
	 */
	public String calculatePayment(IMiniTable payment, boolean isMultiCurrency)
	{
		log.config("");

		//  Payment
		totalPay = Env.ZERO;
		int rows = payment.getRowCount();
		m_noPayments = 0;
		for (int i = 0; i < rows; i++)
		{
			if (((Boolean)payment.getValueAt(i, 0)).booleanValue())
			{
				Timestamp ts = (Timestamp)payment.getValueAt(i, 1);
				//	Modified by Jorge Colmenarez, 2024-03-18 21:24
				//	Update Allocation Date when it's not Multicurrency or not always updated
				if ( !isMultiCurrency && !alwaysUpdateAllocationDate )  // the converted amounts are only valid for the selected date
					allocDate = TimeUtil.max(allocDate, ts);
				else if(alwaysUpdateAllocationDate)
					allocDate = TimeUtil.max(allocDate, ts);
				//	Added by Jorge Colmenarez, 2024-12-12 10:07
				//	Set OpenAmt
				BigDecimal openAmt = (BigDecimal)payment.getValueAt(i, (isMultiCurrency ? 7 : 5));
				BigDecimal bd = (BigDecimal)payment.getValueAt(i, i_payment);
				log.warning("Monto aplicar:"+bd+"  Monto del campo: "+openAmt);
				if(bd.compareTo(BigDecimal.ZERO)<0 && openAmt.compareTo(bd)<=0)
					totalPay = totalPay.add(bd);  //  Applied Pay
				else if(bd.compareTo(BigDecimal.ZERO)>0 && openAmt.compareTo(bd)>=0)
					totalPay = totalPay.add(bd);  //  Applied Pay
				else {
					totalPay = totalPay.add(openAmt);  //  Applied Pay
					if(totalPay.compareTo(bd)!=0)
						payment.setValueAt(totalPay, i, i_payment);
				}
				//	End Jorge Colmenarez
				m_noPayments++;
				if (log.isLoggable(Level.FINE)) log.fine("Payment_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return String.valueOf(m_noPayments) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalPay) + " ";
	}
	
	public String calculateInvoice(IMiniTable invoice, boolean isMultiCurrency)
	{		
		//  Invoices
		totalInv = Env.ZERO;
		int rows = invoice.getRowCount();
		m_noInvoices = 0;

		for (int i = 0; i < rows; i++)
		{
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				Timestamp ts = (Timestamp)invoice.getValueAt(i, 1);
				//	Modified by Jorge Colmenarez, 2024-03-18 21:24
				//	Update Allocation Date when it's not Multicurrency or not always updated
				if ( !isMultiCurrency || !alwaysUpdateAllocationDate )  // the converted amounts are only valid for the selected date
					allocDate = TimeUtil.max(allocDate, ts);
				else if(alwaysUpdateAllocationDate)
					allocDate = TimeUtil.max(allocDate, ts);
				//	End Jorge Colmenarez
				BigDecimal bd = (BigDecimal)invoice.getValueAt(i, i_applied);
				totalInv = totalInv.add(bd);  //  Applied Inv
				m_noInvoices++;
				if (log.isLoggable(Level.FINE)) log.fine("Invoice_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return String.valueOf(m_noInvoices) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalInv) + " ";
	}
	
	/**************************************************************************
	 *  Save Data
	 */
	public MAllocationHdr saveData(int m_WindowNo, Object date, Object dateAcct, IMiniTable payment, IMiniTable invoice, String trxName)
	{
		if (m_noInvoices + m_noPayments == 0)
			return null;

		//  fixed fields
		int AD_Client_ID = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Client_ID");
		int AD_Org_ID = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Org_ID");
		int C_BPartner_ID = m_C_BPartner_ID;
		int C_Order_ID = 0;
		int C_CashLine_ID = 0;
		Timestamp DateTrx = (Timestamp)date;
		Timestamp DateAcct = (Timestamp)dateAcct;
		int C_Currency_ID = m_C_Currency_ID;	//	the allocation currency
		//
		if (AD_Org_ID == 0)
		{
			//ADialog.error(m_WindowNo, this, "Org0NotAllowed", null);
			throw new AdempiereException("@Org0NotAllowed@");
		}
		//
		if (log.isLoggable(Level.CONFIG)) log.config("Client=" + AD_Client_ID + ", Org=" + AD_Org_ID
			+ ", BPartner=" + C_BPartner_ID + ", Date=" + DateTrx + ", DateAcct=" + DateAcct);

		//  Payment - Loop and add them to paymentList/amountList
		int pRows = payment.getRowCount();
		ArrayList<Integer> paymentList = new ArrayList<Integer>(pRows);
		ArrayList<BigDecimal> amountList = new ArrayList<BigDecimal>(pRows);
		BigDecimal paymentAppliedAmt = Env.ZERO;
		for (int i = 0; i < pRows; i++)
		{
			//  Payment line is selected
			if (((Boolean)payment.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)payment.getValueAt(i, 2);   //  Value
				//  Payment variables
				int C_Payment_ID = pp.getKey();
				paymentList.add(new Integer(C_Payment_ID));
				//
				BigDecimal PaymentAmt = (BigDecimal)payment.getValueAt(i, i_payment);  //  Applied Payment
				amountList.add(PaymentAmt);
				//
				paymentAppliedAmt = paymentAppliedAmt.add(PaymentAmt);
				//
				if (log.isLoggable(Level.FINE)) log.fine("C_Payment_ID=" + C_Payment_ID 
					+ " - PaymentAmt=" + PaymentAmt); // + " * " + Multiplier + " = " + PaymentAmtAbs);
			}
		}
		if (log.isLoggable(Level.CONFIG)) log.config("Number of Payments=" + paymentList.size() + " - Total=" + paymentAppliedAmt);

		//  Invoices - Loop and generate allocations
		int iRows = invoice.getRowCount();
		
		//	Create Allocation
		MAllocationHdr alloc = new MAllocationHdr (Env.getCtx(), true,	//	manual
			DateTrx, C_Currency_ID, Env.getContext(Env.getCtx(), "#AD_User_Name"), trxName);
		alloc.setAD_Org_ID(AD_Org_ID);
		alloc.setC_DocType_ID(m_C_DocType_ID);
		alloc.setDescription(alloc.getDescriptionForManualAllocation(m_C_BPartner_ID, trxName));
		//	Added by Jorge Colmenarez, 2021-07-22 17:04 
		//	Support for set DateAcct for CurrentDate, and prevent WrongAllocationDate
		boolean useSysDate = MSysConfig.getBooleanValue("ALLOCATION_USE_SYSDATE_FOR_DATEACCT", true, Env.getAD_Client_ID(Env.getCtx()));
		if(useSysDate)
			alloc.setDateAcct(new Timestamp(System.currentTimeMillis()));
		else
			alloc.setDateAcct(DateAcct);
		//	End Jorge Colmenarez
		alloc.saveEx();
		//	For all invoices
		BigDecimal unmatchedApplied = Env.ZERO;
		for (int i = 0; i < iRows; i++)
		{
			//  Invoice line is selected
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)invoice.getValueAt(i, 2);    //  Value
				//  Invoice variables
				int C_Invoice_ID = pp.getKey();
				MInvoice inv = new MInvoice(Env.getCtx(), C_Invoice_ID, trxName);
				BigDecimal AppliedAmt = (BigDecimal)invoice.getValueAt(i, i_applied);
				//  semi-fixed fields (reset after first invoice)
				BigDecimal DiscountAmt = (BigDecimal)invoice.getValueAt(i, i_discount);
				BigDecimal WriteOffAmt = (BigDecimal)invoice.getValueAt(i, i_writeOff);
				//	OverUnderAmt needs to be in Allocation Currency
				BigDecimal OverUnderAmt = ((BigDecimal)invoice.getValueAt(i, i_open))
					.subtract(AppliedAmt).subtract(DiscountAmt).subtract(WriteOffAmt);
				
				if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " - AppliedAmt=" + AppliedAmt);// + " -> " + AppliedAbs);
				//  loop through all payments until invoice applied
				
				for (int j = 0; j < paymentList.size() && AppliedAmt.signum() != 0; j++)
				{
					int C_Payment_ID = ((Integer)paymentList.get(j)).intValue();
					MFTUPayment pay = new MFTUPayment(Env.getCtx(), C_Payment_ID, trxName);
					if(pay.getC_BPartner_ID() == inv.getC_BPartner_ID())
					{
						BigDecimal PaymentAmt = (BigDecimal)amountList.get(j);
						if (PaymentAmt.signum() == AppliedAmt.signum())	// only match same sign (otherwise appliedAmt increases)
						{												// and not zero (appliedAmt was checked earlier)
							if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + PaymentAmt);
							
							BigDecimal amount = AppliedAmt;
							if (amount.abs().compareTo(PaymentAmt.abs()) > 0)  // if there's more open on the invoice
								amount = PaymentAmt;							// than left in the payment
							
							//	Allocation Line
							MAllocationLine aLine = new MAllocationLine (alloc, amount, 
								DiscountAmt, WriteOffAmt, OverUnderAmt);
							aLine.setDocInfo((pay.getC_BPartner_ID() == 0 ? C_BPartner_ID : pay.getC_BPartner_ID()), C_Order_ID, C_Invoice_ID);
							aLine.setPaymentInfo(C_Payment_ID, C_CashLine_ID);
							aLine.saveEx();

							//  Apply Discounts and WriteOff only first time
							DiscountAmt = Env.ZERO;
							WriteOffAmt = Env.ZERO;
							//  subtract amount from Payment/Invoice
							AppliedAmt = AppliedAmt.subtract(amount);
							PaymentAmt = PaymentAmt.subtract(amount);
							if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + PaymentAmt);
							amountList.set(j, PaymentAmt);  //  update
						}	//	for all applied amounts
					}
					else
						continue;
				}	//	loop through payments for invoice
				
				if ( AppliedAmt.signum() == 0 && DiscountAmt.signum() == 0 && WriteOffAmt.signum() == 0)
					continue;
				else {			// remainder will need to match against other invoices
					int C_Payment_ID = 0;
					
					//	Allocation Line
					MAllocationLine aLine = new MAllocationLine (alloc, AppliedAmt, 
						DiscountAmt, WriteOffAmt, OverUnderAmt);
					aLine.setDocInfo(inv.getC_BPartner_ID(), C_Order_ID, C_Invoice_ID);
					aLine.setPaymentInfo(C_Payment_ID, C_CashLine_ID);
					aLine.saveEx();
					if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + AppliedAmt);
					unmatchedApplied = unmatchedApplied.add(AppliedAmt);
				}
			}   //  invoice selected
		}   //  invoice loop

		// check for unapplied payment amounts (eg from payment reversals)
		for (int i = 0; i < paymentList.size(); i++)	{
			BigDecimal payAmt = (BigDecimal) amountList.get(i);
			if ( payAmt.signum() == 0 )
					continue;
			int C_Payment_ID = ((Integer)paymentList.get(i)).intValue();
			MFTUPayment pay = new MFTUPayment(Env.getCtx(), C_Payment_ID, trxName);
			if (log.isLoggable(Level.FINE)) log.fine("Payment=" + C_Payment_ID  
					+ ", Amount=" + payAmt);

			//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, payAmt, 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.setDocInfo((pay.getC_BPartner_ID() == 0 ? C_BPartner_ID : pay.getC_BPartner_ID()), 0, 0);
			aLine.setPaymentInfo(C_Payment_ID, 0);
			aLine.saveEx();
			unmatchedApplied = unmatchedApplied.subtract(payAmt);
		}		
		
		// check for charge amount
		if ( m_C_Charge_ID > 0 && unmatchedApplied.compareTo(Env.ZERO) != 0 )
		{
			BigDecimal chargeAmt = totalDiff;
	
		//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, chargeAmt.negate(), 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.setC_Charge_ID(m_C_Charge_ID);
			aLine.setC_BPartner_ID(m_C_BPartner_ID);
			//	Added by Jorge Colmenarez, 2024-03-11 15:37
			//	Support for set Activity and Cost Center
			if(m_C_Activity_ID>0)
				aLine.set_ValueOfColumn("C_Activity_ID", m_C_Activity_ID);
			if(m_User1_ID>0)
				aLine.set_ValueOfColumn("User1_ID", m_User1_ID);
			//	End Jorge Colmenarez
			if (!aLine.save(trxName)) {
				StringBuilder msg = new StringBuilder("Allocation Line not saved - Charge=").append(m_C_Charge_ID);
				throw new AdempiereException(msg.toString());
			}
			unmatchedApplied = unmatchedApplied.add(chargeAmt);
		}	
		
		if ( unmatchedApplied.signum() != 0 )
			throw new AdempiereException("Allocation not balanced -- out by " + unmatchedApplied);

		//	Should start WF
		if (alloc.get_ID() != 0)
		{
			if (!alloc.processIt(DocAction.ACTION_Complete))
				throw new AdempiereException("Cannot complete allocation: " + alloc.getProcessMsg());
			alloc.saveEx();
		}
		
		//  Test/Set IsPaid for Invoice - requires that allocation is posted
		for (int i = 0; i < iRows; i++)
		{
			//  Invoice line is selected
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)invoice.getValueAt(i, 2);    //  Value
				//  Invoice variables
				int C_Invoice_ID = pp.getKey();
				String sql = "SELECT invoiceOpenConverted(C_Invoice_ID, "+m_C_Currency_ID+") "
					+ "FROM C_Invoice WHERE C_Invoice_ID=?";
				BigDecimal open = DB.getSQLValueBD(trxName, sql, C_Invoice_ID);
				if (open != null && open.signum() == 0)	 {
					sql = "UPDATE C_Invoice SET IsPaid='Y' "
						+ "WHERE C_Invoice_ID=" + C_Invoice_ID;
					int no = DB.executeUpdate(sql, trxName);
					if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " is paid - updated=" + no);
				} else {
					if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " is not paid - " + open);
				}
			}
		}
		//  Test/Set Payment is fully allocated
		for (int i = 0; i < paymentList.size(); i++)
		{
			int C_Payment_ID = ((Integer)paymentList.get(i)).intValue();
			MFTUPayment pay = new MFTUPayment (Env.getCtx(), C_Payment_ID, trxName);
			if (pay.testAllocation())
				pay.saveEx();
			if (log.isLoggable(Level.CONFIG)) log.config("Payment #" + i + (pay.isAllocated() ? " not" : " is") 
					+ " fully allocated");
		}
		MBPartner bpartner = new MBPartner(Env.getCtx(), m_C_BPartner_ID, trxName);
		bpartner.setTotalOpenBalance();
		bpartner.saveEx();
		paymentList.clear();
		amountList.clear();
		
		return alloc;
	}   //  saveData
	
	@Override
	protected void initForm() {
	}
	
	/**
	 * Get Activity for Allocation
	 * @return ArrayList
	 */
	public ArrayList<KeyNamePair> getActivities()
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**	Activity	**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT a.C_Activity_ID,a.Value||' - '||a.Name as Activity FROM C_Activity a WHERE a.IsSummary = 'N' AND a.IsActive = 'Y' ORDER BY a.Value", "a",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = new KeyNamePair(0, "");
			data.add(dt);
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		return data;
	}
	
	/**
	 * Get Cost Center by Activity for Allocation
	 * @return ArrayList
	 */
	public ArrayList<KeyNamePair> getCostCenter(int ActivityID)
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**	Cost Center	**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT a.C_ElementValue_ID,a.Value||' - '||a.Name as CostCenter FROM C_ElementValue a "
				+ "JOIN FTU_Activity_User1_Access b ON (a.C_ElementValue_ID = b.User1_ID) "
				+ "WHERE a.IsSummary = 'N' AND a.IsActive = 'Y' "
				+ "AND b.C_Activity_ID = "+ActivityID+" "
				+ "ORDER BY a.Value", "a",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = new KeyNamePair(0, "");
			data.add(dt);
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		return data;
	}
}
