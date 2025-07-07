package net.frontuari.bpallocation.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MInvoice;
import org.compiere.util.DB;

public class MFTUAllocationLine extends MAllocationLine {

	private static final long serialVersionUID = 7355960922683592237L;

	public MFTUAllocationLine(Properties ctx, String C_AllocationLine_UU, String trxName) {
		super(ctx, C_AllocationLine_UU, trxName);
	}

	public MFTUAllocationLine(Properties ctx, int C_AllocationLine_ID, String trxName) {
		super(ctx, C_AllocationLine_ID, trxName);
	}

	public MFTUAllocationLine(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	public MFTUAllocationLine(MAllocationHdr parent) {
		super(parent);
	}

	public MFTUAllocationLine(MAllocationHdr parent, BigDecimal Amount, BigDecimal DiscountAmt, BigDecimal WriteOffAmt,
			BigDecimal OverUnderAmt) {
		super(parent, Amount, DiscountAmt, WriteOffAmt, OverUnderAmt);
	}
	
	/** Allocation Header		*/
	private MFTUAllocationHeader	m_parent = null;
	
	/**
	 * 	Get Parent
	 *	@return parent
	 */
	public MFTUAllocationHeader getParent()
	{
		if (m_parent == null)
			m_parent = new MFTUAllocationHeader (getCtx(), getC_AllocationHdr_ID(), get_TrxName());
		return m_parent;
	}	//	getParent
	
	/**
	 * 	Set Parent
	 *	@param parent parent
	 */
	protected void setParent (MFTUAllocationHeader parent)
	{
		m_parent = parent;
	}	//	setParent
	
	/**
	 * 	Process Allocation (does not update line). <br/>
	 * 	- Update and Link Invoice/Payment/Cash.
	 * 	@param reverse if true allocation is reversed
	 *	@return C_BPartner_ID
	 */
	protected int processIt (boolean reverse)
	{
		if (log.isLoggable(Level.FINE)) log.fine("Reverse=" + reverse + " - " + toString());
		int C_Invoice_ID = getC_Invoice_ID();
		MInvoice invoice = getInvoice();
		if (invoice != null 
			&& getC_BPartner_ID() != invoice.getC_BPartner_ID())
			setC_BPartner_ID(invoice.getC_BPartner_ID());
		//
		int C_Payment_ID = getC_Payment_ID();
		int C_CashLine_ID = getC_CashLine_ID();
		
		//	Update Payment
		if (C_Payment_ID != 0)
		{
			MFTUPayment payment = new MFTUPayment (getCtx(), C_Payment_ID, get_TrxName());
			if (getC_BPartner_ID() != payment.getC_BPartner_ID())
				log.warning("C_BPartner_ID different - Invoice=" + getC_BPartner_ID() + " - Payment=" + payment.getC_BPartner_ID());
			if (reverse)
			{
				if (!payment.isCashbookTrx())
				{
					payment.setIsAllocated(false);
					payment.saveEx();
				}
			}
			else
			{
				if (payment.testAllocation())
					payment.saveEx();
			}
		}
		
		//	Payment - Invoice
		if (C_Payment_ID != 0 && invoice != null)
		{
			//	Link to Invoice
			if (reverse)
			{
				invoice.setC_Payment_ID(0);
				if (log.isLoggable(Level.FINE)) log.fine("C_Payment_ID=" + C_Payment_ID
					+ " Unlinked from C_Invoice_ID=" + C_Invoice_ID);
			}
			else if (invoice.isPaid())
			{
				invoice.setC_Payment_ID(C_Payment_ID);
				if (log.isLoggable(Level.FINE)) log.fine("C_Payment_ID=" + C_Payment_ID
					+ " Linked to C_Invoice_ID=" + C_Invoice_ID);
			}
			
			//	Link to Order
			String update = "UPDATE C_Order o "
				+ "SET C_Payment_ID=" 
					+ (reverse ? "NULL " : "(SELECT C_Payment_ID FROM C_Invoice WHERE C_Invoice_ID=" + C_Invoice_ID + ") ")
				+ "WHERE o.C_Order_ID = (SELECT i.C_Order_ID FROM C_Invoice i "
					+ "WHERE i.C_Invoice_ID=" + C_Invoice_ID + ")";
			if (DB.executeUpdate(update, get_TrxName()) > 0)
				if (log.isLoggable(Level.FINE)) log.fine("C_Payment_ID=" + C_Payment_ID 
					+ (reverse ? " UnLinked from" : " Linked to")
					+ " order of C_Invoice_ID=" + C_Invoice_ID);
		}
		
		//	Cash - Invoice
		if (C_CashLine_ID != 0 && invoice != null)
		{
			//	Link to Invoice
			if (reverse)
			{
				invoice.setC_CashLine_ID(0);
				if (log.isLoggable(Level.FINE)) log.fine("C_CashLine_ID=" + C_CashLine_ID 
					+ " Unlinked from C_Invoice_ID=" + C_Invoice_ID);
			}
			else
			{
				invoice.setC_CashLine_ID(C_CashLine_ID);
				if (log.isLoggable(Level.FINE)) log.fine("C_CashLine_ID=" + C_CashLine_ID 
					+ " Linked to C_Invoice_ID=" + C_Invoice_ID);
			}
			
			//	Link to Order
			String update = "UPDATE C_Order o "
				+ "SET C_CashLine_ID="
					+ (reverse ? "NULL " : "(SELECT C_CashLine_ID FROM C_Invoice WHERE C_Invoice_ID=" + C_Invoice_ID + ") ")
				+ "WHERE o.C_Order_ID = (SELECT i.C_Order_ID FROM C_Invoice i "
					+ "WHERE i.C_Invoice_ID=" + C_Invoice_ID + ")";
			if (DB.executeUpdate(update, get_TrxName()) > 0)
				if (log.isLoggable(Level.FINE)) log.fine("C_CashLine_ID=" + C_CashLine_ID 
					+ (reverse ? " UnLinked from" : " Linked to")
					+ " order of C_Invoice_ID=" + C_Invoice_ID);
		}		
		
		//	Update Balance / Credit used - Counterpart of MInvoice.completeIt
		if (invoice != null)
		{
			if (invoice.testAllocation()
				&& !invoice.save())
				log.log(Level.SEVERE, "Invoice not updated - " + invoice);
		}
		
		return getC_BPartner_ID();
	}	//	processIt

}
