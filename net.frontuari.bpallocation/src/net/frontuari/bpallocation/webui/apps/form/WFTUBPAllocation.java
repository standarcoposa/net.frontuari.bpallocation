/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package net.frontuari.bpallocation.webui.apps.form;

import static org.adempiere.webui.ClientInfo.SMALL_WIDTH;
import static org.adempiere.webui.ClientInfo.maxWidth;
import static org.compiere.model.SystemIDs.COLUMN_C_INVOICE_C_BPARTNER_ID;
import static org.compiere.model.SystemIDs.COLUMN_C_INVOICE_C_CURRENCY_ID;
import static org.compiere.model.SystemIDs.COLUMN_C_PERIOD_AD_ORG_ID;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.DocumentLink;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.WListbox;
import org.adempiere.webui.editor.WDateEditor;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.CustomForm;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MSysConfig;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.compiere.util.Util;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.North;
import org.zkoss.zul.Separator;
import org.zkoss.zul.South;
import org.zkoss.zul.Space;

import net.frontuari.bpallocation.model.MFTUAllocationHeader;

/**
 * Allocation Form
 *
 * @author  Jorg Janke
 * @version $Id: VAllocation.java,v 1.2 2006/07/30 00:51:28 jjanke Exp $
 * 
 * Contributor : Fabian Aguilar - OFBConsulting - Multiallocation
 * Contributor : Jorge Colmenarez - Frontuari, C.A. - Multiallocation between BPartner documents
 */
public class WFTUBPAllocation extends FTUBPAllocation implements ValueChangeListener, WTableModelListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2788684836818264183L;

	private CustomForm form = new CustomForm();

	/**
	 *	Initialize Panel
	 *  @param WindowNo window
	 *  @param frame frame
	 */
	public WFTUBPAllocation()
	{
		Env.setContext(Env.getCtx(), form.getWindowNo(), "IsSOTrx", "Y");   //  defaults to no
		try
		{
			super.dynInit();
			dynInit();
			zkInit();
			calculate();
			southPanel.appendChild(new Separator());
			southPanel.appendChild(statusBar);
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
		}
	}	//	init
	
	//
	private Borderlayout mainLayout = new Borderlayout();
	private Panel parameterPanel = new Panel();
	private Panel allocationPanel = new Panel();
	private Grid parameterLayout = GridFactory.newGridLayout();
	private Label bpartnerLabel = new Label();
	private WSearchEditor bpartnerSearch = null;
	private Label bpartnerLabel2 = new Label();
	private WSearchEditor bpartnerSearch2 = null;
	private WListbox invoiceTable = ListboxFactory.newDataTable();
	private WListbox paymentTable = ListboxFactory.newDataTable();
	private Borderlayout infoPanel = new Borderlayout();
	private Panel paymentPanel = new Panel();
	private Panel invoicePanel = new Panel();
	private Label paymentLabel = new Label();
	private Label invoiceLabel = new Label();
	private Borderlayout paymentLayout = new Borderlayout();
	private Borderlayout invoiceLayout = new Borderlayout();
	private Label paymentInfo = new Label();
	private Label invoiceInfo = new Label();
	private Grid allocationLayout = GridFactory.newGridLayout();
	private Label differenceLabel = new Label();
	private Textbox differenceField = new Textbox();
	private Button allocateButton = new Button();
	private Button refreshButton = new Button();
	private Label currencyLabel = new Label();
	private WTableDirEditor currencyPick = null;
	private Checkbox multiCurrency = new Checkbox();
	private Label chargeLabel = new Label();
	private WTableDirEditor chargePick = null;
	private Label DocTypeLabel = new Label();
	private WTableDirEditor DocTypePick = null;
	private Label allocCurrencyLabel = new Label();
	private Hlayout statusBar = new Hlayout();
	private Label dateLabel = new Label();
	private WDateEditor dateField = new WDateEditor();
	private Label dateAcctLabel = new Label();
	private WDateEditor dateAcctField = new WDateEditor();
	private Checkbox autoWriteOff = new Checkbox();
	private Label organizationLabel = new Label();
	private WTableDirEditor organizationPick;
	
	private Panel southPanel = new Panel();
	//	Added by Jorge Colmenarez, 2024-01-15 17:48
	//	Support request #12 GSS for filter by DocType Access of Payments and Invoices
	private Checkbox docTypeFilter = new Checkbox();
	private Label docTypePaymentLabel = new Label();
	private WTableDirEditor docTypePaymentSearch = null;
	private Label docTypeInvoiceLabel = new Label();
	private WTableDirEditor docTypeInvoiceSearch = null;
	//	Added by Jorge Colmenarez 2024-03-08 16:35 
	//	support for selected Activity and Cost Center when Charge it's Selected
	private Label activityLabel = new Label();
	private Listbox activityPick = ListboxFactory.newDropdownListbox();
	private Label costcenterLabel = new Label();
	private Listbox costcenterPick = ListboxFactory.newDropdownListbox();
	private boolean showActivityAndCostCenter = false;
	//	End Jorge Colmenarez

	/**
	 *  Static Init
	 *  @throws Exception
	 */
	private void zkInit() throws Exception
	{
		//
		form.appendChild(mainLayout);
		ZKUpdateUtil.setWidth(mainLayout, "99%");
		ZKUpdateUtil.setHeight(mainLayout, "100%");
		dateLabel.setText(Msg.getMsg(Env.getCtx(), "Date"));
		dateAcctLabel.setText(Msg.translate(Env.getCtx(), "DateAcct"));
		autoWriteOff.setSelected(false);
		autoWriteOff.setText(Msg.getMsg(Env.getCtx(), "AutoWriteOff", true));
		autoWriteOff.setTooltiptext(Msg.getMsg(Env.getCtx(), "AutoWriteOff", false));
		//
		parameterPanel.appendChild(parameterLayout);
		allocationPanel.appendChild(allocationLayout);
		bpartnerLabel.setText(Msg.translate(Env.getCtx(), "C_BPartner_ID"));
		bpartnerLabel2.setText(Msg.translate(Env.getCtx(), "C_BPartnerRelation_ID"));
		paymentLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Payment_ID"));
		invoiceLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Invoice_ID"));
		paymentPanel.appendChild(paymentLayout);
		invoicePanel.appendChild(invoiceLayout);
		invoiceInfo.setText(".");
		paymentInfo.setText(".");
		chargeLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Charge_ID"));
		DocTypeLabel.setText(" " + Msg.translate(Env.getCtx(), "C_DocType_ID"));	
		differenceLabel.setText(Msg.getMsg(Env.getCtx(), "Difference"));
		differenceField.setText("0");
		differenceField.setReadonly(true);
		differenceField.setStyle("text-align: right");
		allocateButton.setLabel(Util.cleanAmp(Msg.getMsg(Env.getCtx(), "Process")));
		allocateButton.addActionListener(this);
		refreshButton.setLabel(Util.cleanAmp(Msg.getMsg(Env.getCtx(), "Refresh")));
		refreshButton.addActionListener(this);
		refreshButton.setAutodisable("self");
		currencyLabel.setText(Msg.translate(Env.getCtx(), "C_Currency_ID"));
		multiCurrency.setText(Msg.getMsg(Env.getCtx(), "MultiCurrency"));
		multiCurrency.addActionListener(this);
		allocCurrencyLabel.setText(".");
		
		organizationLabel.setText(Msg.translate(Env.getCtx(), "AD_Org_ID"));
		//	Added by Jorge Colmenarez, 2024-01-15 17:48
		//	Support request #12 GSS for filter by DocType Access of Payments and Invoices
		if(filterbyDocType) {
			docTypeFilter.setText(Msg.getMsg(Env.getCtx(), "docType.filter"));
			docTypeFilter.setChecked(true);
			docTypeFilter.addActionListener(this);
			docTypePaymentLabel.setText(" " + Msg.translate(Env.getCtx(), "C_DocTypePayment_ID"));
			docTypeInvoiceLabel.setText(" " + Msg.translate(Env.getCtx(), "C_DocTypeInvoice_ID"));
		}
		//	Added by Jorge colmenarez, 2024-03-08 17:43
		activityLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Activity_ID"));
		costcenterLabel.setText(" " + Msg.translate(Env.getCtx(), "User1_ID"));
		//	End Jorge Colmenarez
		
		North north = new North();
		north.setStyle("border: none");
		mainLayout.appendChild(north);
		north.appendChild(parameterPanel);
		
		Rows rows = null;
		Row row = null;
		
		ZKUpdateUtil.setWidth(parameterLayout, "100%");
		rows = parameterLayout.newRows();
		row = rows.newRow();
		row.appendCellChild(organizationLabel.rightAlign());
		ZKUpdateUtil.setHflex(organizationPick.getComponent(), "true");
		row.appendCellChild(organizationPick.getComponent(),2);
		organizationPick.showMenu();	
		organizationPick.setValue(Env.getContextAsInt(Env.getCtx(), "#AD_Org_ID"));
		row.appendCellChild(new Space(),1);		
		Hbox box = new Hbox();
		box.appendChild(dateLabel.rightAlign());
		box.appendChild(dateField.getComponent());
		row.appendCellChild(box,2);
				
		boolean useSysDate = MSysConfig.getBooleanValue("ALLOCATION_USE_SYSDATE_FOR_DATEACCT", true, Env.getAD_Client_ID(Env.getCtx()));
		if(!useSysDate)
		{
			Hbox box2 = new Hbox();
			box2.appendChild(dateAcctLabel.rightAlign());
			box2.appendChild(dateAcctField.getComponent());
			row.appendCellChild(box2,2);
		}
		
		row = rows.newRow();
		row.appendCellChild(bpartnerLabel.rightAlign());
		ZKUpdateUtil.setHflex(bpartnerSearch.getComponent(), "true");
		row.appendCellChild(bpartnerSearch.getComponent(),2);
		bpartnerSearch.showMenu();
		row.appendCellChild(bpartnerLabel2.rightAlign());
		ZKUpdateUtil.setHflex(bpartnerSearch2.getComponent(), "true");
		row.appendCellChild(bpartnerSearch2.getComponent(),2);
		
		row = rows.newRow();
		row.appendCellChild(currencyLabel.rightAlign(),1);
		ZKUpdateUtil.setHflex(currencyPick.getComponent(), "true");
		row.appendCellChild(currencyPick.getComponent(),1);		
		currencyPick.showMenu();
		row.appendCellChild(multiCurrency,1);		
		row.appendCellChild(autoWriteOff,2);
		row.appendCellChild(new Space(),1);

		//	Added by Jorge Colmenarez, 2024-01-15 17:48
		//	Support request #12 GSS for filter by DocType Access of Payments and Invoices
		if(filterbyDocType) {
			row = rows.newRow();
			Hbox cbox = new Hbox();
			cbox.setWidth("100%");
			cbox.setPack("end");
			cbox.appendChild(docTypeFilter);
			row.appendCellChild(cbox, 2);
			row.appendCellChild(docTypePaymentLabel.rightAlign());
			ZKUpdateUtil.setHflex(docTypePaymentSearch.getComponent(), "true");
			row.appendCellChild(docTypePaymentSearch.getComponent(),1);
			docTypePaymentSearch.showMenu();
			row.appendCellChild(docTypeInvoiceLabel.rightAlign());
			ZKUpdateUtil.setHflex(docTypeInvoiceSearch.getComponent(), "true");
			row.appendCellChild(docTypeInvoiceSearch.getComponent(),1);
			docTypeInvoiceSearch.showMenu();
			LayoutUtils.expandTo(parameterLayout, 3, true);
		}
		//	End Jorge Colmenarez
		
		South south = new South();
		south.setStyle("border: none");
		mainLayout.appendChild(south);
		south.appendChild(southPanel);
		southPanel.appendChild(allocationPanel);
		allocationPanel.appendChild(allocationLayout);
		ZKUpdateUtil.setHflex(allocationLayout, "min");
		rows = allocationLayout.newRows();
		row = rows.newRow();
		row.appendCellChild(chargeLabel.rightAlign());
		ZKUpdateUtil.setHflex(chargePick.getComponent(), "true");
		row.appendCellChild(chargePick.getComponent());
		chargePick.showMenu();
		//	Added by Jorge Colmenarez, 2024-03-08 16:47
		//	Show Activity and Cost Center
		showActivityAndCostCenter = MSysConfig.getBooleanValue("ShowActivityAndCostCenterOnAllocation", false, Env.getAD_Client_ID(Env.getCtx()), Env.getAD_Org_ID(Env.getCtx()));
		if(showActivityAndCostCenter) {
			if (maxWidth(SMALL_WIDTH-1))
				row = rows.newRow();
			row.appendCellChild(activityLabel.rightAlign());
			ZKUpdateUtil.setHflex(activityPick, "true");
			row.appendCellChild(activityPick);
			if (maxWidth(SMALL_WIDTH-1))
				row = rows.newRow();
			row.appendCellChild(costcenterLabel.rightAlign());
			ZKUpdateUtil.setHflex(costcenterPick, "true");
			row.appendCellChild(costcenterPick);
		}
		//	End Jorge Colmenarez
		row.appendCellChild(DocTypeLabel.rightAlign());
		ZKUpdateUtil.setHflex(DocTypePick.getComponent(), "true");
		row.appendCellChild(DocTypePick.getComponent());
		DocTypePick.showMenu();
		row = rows.newRow();
		row.appendCellChild(differenceLabel.rightAlign());
		row.appendCellChild(allocCurrencyLabel.rightAlign());
		ZKUpdateUtil.setHflex(differenceField, "true");
		row.appendCellChild(differenceField);
		row.appendCellChild(new Space(),1);
		ZKUpdateUtil.setHflex(allocateButton, "true");
		row.appendCellChild(allocateButton);
		row.appendCellChild(refreshButton);
		
		paymentPanel.appendChild(paymentLayout);
		ZKUpdateUtil.setWidth(paymentPanel, "100%");
		ZKUpdateUtil.setHeight(paymentPanel, "100%");
		ZKUpdateUtil.setWidth(paymentLayout, "100%");
		ZKUpdateUtil.setHeight(paymentLayout, "100%");
		paymentLayout.setStyle("border: none");
		
		invoicePanel.appendChild(invoiceLayout);
		ZKUpdateUtil.setWidth(invoicePanel, "100%");
		ZKUpdateUtil.setHeight(invoicePanel, "100%");
		ZKUpdateUtil.setWidth(invoiceLayout, "100%");
		ZKUpdateUtil.setHeight(invoiceLayout, "100%");
		invoiceLayout.setStyle("border: none");
		
		north = new North();
		north.setStyle("border: none");
		paymentLayout.appendChild(north);
		north.appendChild(paymentLabel);
		south = new South();
		south.setStyle("border: none");
		paymentLayout.appendChild(south);
		south.appendChild(paymentInfo.rightAlign());
		Center center = new Center();
		paymentLayout.appendChild(center);
		center.appendChild(paymentTable);
		ZKUpdateUtil.setWidth(paymentTable, "99%");
		//ZKUpdateUtil.setHeight(paymentTable, "99%");
		center.setStyle("border: none");
		
		north = new North();
		north.setStyle("border: none");
		invoiceLayout.appendChild(north);
		north.appendChild(invoiceLabel);
		south = new South();
		south.setStyle("border: none");
		invoiceLayout.appendChild(south);
		south.appendChild(invoiceInfo.rightAlign());
		center = new Center();
		invoiceLayout.appendChild(center);
		center.appendChild(invoiceTable);
		ZKUpdateUtil.setWidth(invoiceTable, "99%");
		//ZKUpdateUtil.setHeight(invoiceTable, "99%");
		center.setStyle("border: none");
		//
		center = new Center();
		mainLayout.appendChild(center);
		center.appendChild(infoPanel);
		ZKUpdateUtil.setHflex(infoPanel, "1");
		ZKUpdateUtil.setVflex(infoPanel, "1");
		
		infoPanel.setStyle("border: none");
		ZKUpdateUtil.setWidth(infoPanel, "100%");
		ZKUpdateUtil.setHeight(infoPanel, "100%");
		
		north = new North();
		north.setStyle("border: none");
		ZKUpdateUtil.setHeight(north, "49%");
		infoPanel.appendChild(north);
		north.appendChild(paymentPanel);
		north.setSplittable(true);
		center = new Center();
		center.setStyle("border: none");
		infoPanel.appendChild(center);
		center.appendChild(invoicePanel);
		ZKUpdateUtil.setHflex(invoicePanel, "1");
		ZKUpdateUtil.setVflex(invoicePanel, "1");
	}   //  jbInit

	/**
	 *  Dynamic Init (prepare dynamic fields)
	 *  @throws Exception if Lookups cannot be initialized
	 */
	public void dynInit() throws Exception
	{
		//  Currency
		int AD_Column_ID = COLUMN_C_INVOICE_C_CURRENCY_ID;    //  C_Invoice.C_Currency_ID
		MLookup lookupCur = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
		currencyPick = new WTableDirEditor("C_Currency_ID", true, false, true, lookupCur);
		currencyPick.setValue(new Integer(m_C_Currency_ID));
		currencyPick.addValueChangeListener(this);

		// Organization filter selection
		AD_Column_ID = COLUMN_C_PERIOD_AD_ORG_ID; //C_Period.AD_Org_ID (needed to allow org 0)
		MLookup lookupOrg = MLookupFactory.get(Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
		organizationPick = new WTableDirEditor("AD_Org_ID", true, false, true, lookupOrg);
		organizationPick.setValue(Env.getAD_Org_ID(Env.getCtx()));
		organizationPick.addValueChangeListener(this);
		
		//  BPartner
		AD_Column_ID = COLUMN_C_INVOICE_C_BPARTNER_ID;        //  C_Invoice.C_BPartner_ID
		MLookup lookupBP = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.Search);
		bpartnerSearch = new WSearchEditor("C_BPartner_ID", true, false, true, lookupBP);
		bpartnerSearch.addValueChangeListener(this);
		
	    //  BPartner2
		AD_Column_ID = COLUMN_C_INVOICE_C_BPARTNER_ID;        //  C_Invoice.C_BPartner_ID
		MLookup lookupBP2 = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.Search);
		bpartnerSearch2 = new WSearchEditor("C_BPartner_ID", true, false, true, lookupBP2);
		bpartnerSearch2.addValueChangeListener(this);

		//  Translation
		statusBar.appendChild(new Label(Msg.getMsg(Env.getCtx(), "AllocateStatus")));
		ZKUpdateUtil.setVflex(statusBar, "min");
		
		//  Date set to Login Date
		Calendar cal = Calendar.getInstance();
		cal.setTime(Env.getContextAsDate(Env.getCtx(), "#Date"));
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		dateField.setValue(new Timestamp(cal.getTimeInMillis()));
		dateField.addValueChangeListener(this);
		dateAcctField.setValue(new Timestamp(cal.getTimeInMillis()));
		
		//  Charge
		AD_Column_ID = 61804;    //  C_AllocationLine.C_Charge_ID
		MLookup lookupCharge = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
		chargePick = new WTableDirEditor("C_Charge_ID", false, false, true, lookupCharge);
		chargePick.setValue(new Integer(m_C_Charge_ID));
		chargePick.addValueChangeListener(this);
		
		//	Added by Jorge Colmenarez, 2024-03-08 16:39
		// Activity selection
		ArrayList<KeyNamePair> activityData = getActivities();
		for(KeyNamePair pp : activityData)
			activityPick.appendItem(pp.getName(), pp);
		activityPick.setSelectedIndex(0);
		activityPick.setEnabled(false);
		activityPick.addActionListener(this);
		// Cost Center selection
		KeyNamePair activity = (KeyNamePair) activityPick.getSelectedItem().getValue();
		int activityID = activity.getKey();
		ArrayList<KeyNamePair> costcenterData = getCostCenter(activityID);
		for(KeyNamePair pp : costcenterData)
			costcenterPick.appendItem(pp.getName(), pp);
		costcenterPick.setSelectedIndex(0);
		costcenterPick.setEnabled(false);
		costcenterPick.addActionListener(this);
		//	End Jorge Colmenarez
		
		//  Charge
		AD_Column_ID = 212213;    //  C_AllocationLine.C_Charge_ID
		MLookup lookupDocType = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
		DocTypePick = new WTableDirEditor("C_DocType_ID", false, false, true, lookupDocType);
		DocTypePick.setValue(new Integer(m_C_DocType_ID));
		DocTypePick.addValueChangeListener(this);
			
		//	Added by Jorge Colmenarez, 2024-01-15 17:48
		//	Support request #12 GSS for filter by DocType Access of Payments and Invoices
		if(filterbyDocType) {
			AD_Column_ID = 5302;    //  C_Payment.C_DocType_ID
			MLookup lookupDocTypePayment = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
			docTypePaymentSearch = new WTableDirEditor("C_DocTypePayment_ID", false, false, true, lookupDocTypePayment);
			docTypePaymentSearch.addValueChangeListener(this);
			AD_Column_ID = 3781;    //  C_Invoice.C_DocTypeTarget_ID
			MLookup lookupDocTypeInvoice = MLookupFactory.get (Env.getCtx(), form.getWindowNo(), 0, AD_Column_ID, DisplayType.TableDir);
			docTypeInvoiceSearch = new WTableDirEditor("C_DocTypeInvoice_ID", false, false, true, lookupDocTypeInvoice);
			docTypeInvoiceSearch.addValueChangeListener(this);
		}
			
	}   //  dynInit
	
	/**************************************************************************
	 *  Action Listener.
	 *  - MultiCurrency
	 *  - Allocate
	 *  @param e event
	 */
	public void onEvent(Event e)
	{
		log.config("");
		if (e.getTarget().equals(multiCurrency))
		{
			loadBPartner();
			loadBPartner2();
		}
		//	Allocate
		else if (e.getTarget().equals(allocateButton))
		{
			allocateButton.setEnabled(false);
			MFTUAllocationHeader allocation = saveData();
			loadBPartner();
			loadBPartner2();
			allocateButton.setEnabled(true);
			if (allocation != null) 
			{
				DocumentLink link = new DocumentLink(allocation.getDocumentNo(), allocation.get_Table_ID(), allocation.get_ID());				
				statusBar.appendChild(link);
			}					
		}
		else if(e.getTarget().equals(activityPick)) {
			KeyNamePair activity = activityPick.getSelectedItem().getValue();
			m_C_Activity_ID = activity.getKey();
			if(m_C_Activity_ID>0) {
				costcenterPick.removeAllItems();
				ArrayList<KeyNamePair> costcenterData = getCostCenter(m_C_Activity_ID);
				for(KeyNamePair pp : costcenterData)
					costcenterPick.appendItem(pp.getName(), pp);
				costcenterPick.setSelectedIndex(0);
				costcenterPick.focus();
			}
		}
		else if(e.getTarget().equals(costcenterPick)) {
			KeyNamePair costcenter = costcenterPick.getSelectedItem().getValue();
			m_User1_ID = costcenter.getKey();
		}
		else if (e.getTarget().equals(refreshButton))
		{
			loadBPartner();
			loadBPartner2();
		}
	}   //  actionPerformed

	/**
	 *  Table Model Listener.
	 *  - Recalculate Totals
	 *  @param e event
	 */
	public void tableChanged(WTableModelEvent e)
	{
		boolean isUpdate = (e.getType() == WTableModelEvent.CONTENTS_CHANGED);
		//  Not a table update
		if (!isUpdate)
		{
			calculate();
			return;
		}
		
		int row = e.getFirstRow();
		int col = e.getColumn();
	
		if (row < 0)
			return;
		
		boolean isInvoice = (e.getModel().equals(invoiceTable.getModel()));
		boolean isAutoWriteOff = autoWriteOff.isSelected();
		
		String msg = writeOff(row, col, isInvoice, paymentTable, invoiceTable, isAutoWriteOff);
		
		//render row
		ListModelTable model = isInvoice ? invoiceTable.getModel() : paymentTable.getModel(); 
		model.updateComponent(row);
	    
		if(msg != null && msg.length() > 0)
			FDialog.warn(form.getWindowNo(), "AllocationWriteOffWarn");
		
		calculate();
	}   //  tableChanged
	
	/**
	 *  Vetoable Change Listener.
	 *  - Business Partner
	 *  - Currency
	 * 	- Date
	 *  @param e event
	 */
	public void valueChange (ValueChangeEvent e)
	{
		String name = e.getPropertyName();
		Object value = e.getNewValue();
		if (log.isLoggable(Level.CONFIG)) log.config(name + "=" + value);
		if (value == null && (!name.equals("C_Charge_ID")||!name.equals("C_DocType_ID") ))
			return;
		
		// Organization
		if (name.equals("AD_Org_ID"))
		{
			m_AD_Org_ID = ((Integer) value).intValue();
			
			loadBPartner();
			loadBPartner2 ();
		}
		//		Charge
		else if (name.equals("C_Charge_ID") )
		{
			m_C_Charge_ID = value!=null? ((Integer) value).intValue() : 0;
			//	Added by Jorge Colmenarez, 2024-03-08 16:43
			//	Set Enable Activity and CostCenter
			if(m_C_Charge_ID>0) {
				activityPick.setEnabled(true);
				costcenterPick.setEnabled(true);
			}else {
				activityPick.setEnabled(false);
				costcenterPick.setEnabled(false);
				activityPick.setSelectedIndex(0);
				costcenterPick.setSelectedIndex(0);
			}
			//	End Jorge Colmenarez
			setAllocateButton();
		}

		else if (name.equals("C_DocType_ID") )
		{
			m_C_DocType_ID = value!=null? ((Integer) value).intValue() : 0;
			
		}

		//  BPartner1
		if (e.getSource().equals(bpartnerSearch))
		{
			bpartnerSearch.setValue(value);
			m_C_BPartner_ID = ((Integer)value).intValue();
			loadBPartner();
		}
		//  BPartner2
		else if (e.getSource().equals(bpartnerSearch2))
		{
			bpartnerSearch2.setValue(value);
			m_C_BPartner2_ID = ((Integer)value).intValue();
			loadBPartner2();
		}
		//	Currency
		else if (name.equals("C_Currency_ID"))
		{
			m_C_Currency_ID = ((Integer)value).intValue();
			loadBPartner();
			loadBPartner2();
		}
		//	Date for Multi-Currency
		else if (name.equals("Date") && multiCurrency.isSelected())
		{
			//	LoadBPartner when Not Always Update AllocationDate
			if(!alwaysUpdateAllocationDate) {
				loadBPartner();
				loadBPartner2();	
			}
		}
		//	Added by Jorge Colmenarez, 2024-01-18 11:00
		//	Apply search when DocType Payment or Invoice Changed
		if (name.equals("C_DocTypePayment_ID")) {
			loadBPartner();
			loadBPartner2();
		}
		if (name.equals("C_DocTypeInvoice_ID")) {
			loadBPartner();
			loadBPartner2();
		}
		//	End Jorge Colmenarez
	}   //  vetoableChange
	
	private void setAllocateButton() {
		if (totalDiff.signum() == 0 ^ m_C_Charge_ID > 0 )
		{
			allocateButton.setEnabled(true);
		// chargePick.setValue(m_C_Charge_ID);
		}
		else
		{
			allocateButton.setEnabled(false);
		}

		if ( totalDiff.signum() == 0 )
		{
				chargePick.setValue(null);
				m_C_Charge_ID = 0;
   		}
	}
	/**
	 *  Load Business Partner Info
	 *  - Payments
	 *  - Invoices
	 */
	private void loadBPartner ()
	{
		checkBPartner();
		//	Added by Jorge Colmenarez, 2024-01-18 11:04
		//	Support for filter by Payment DocType
		int docTypePaymentId = 0;
		if (filterbyDocType) {
			if(docTypePaymentSearch.getValue() != null)
				docTypePaymentId = (Integer)docTypePaymentSearch.getValue();
		}
		Vector<Vector<Object>> data = getPaymentData(multiCurrency.isSelected(), dateField.getValue(), paymentTable, docTypeFilter.isSelected(), docTypePaymentId);
		//	End Jorge Colmenarez
		Vector<String> columnNames = getPaymentColumnNames(multiCurrency.isSelected());
		
		paymentTable.clear();
		
		//  Remove previous listeners
		paymentTable.getModel().removeTableModelListener(this);
		
		//  Set Model
		ListModelTable modelP = new ListModelTable(data);
		modelP.addTableModelListener(this);
		paymentTable.setData(modelP, columnNames);
		setPaymentColumnClass(paymentTable, multiCurrency.isSelected());
		//

		//	Added by Jorge Colmenarez, 2024-01-18 11:04
		//	Support for filter by Invoice DocType
		int docTypeInvoiceId = 0;
		if (filterbyDocType) {
			if(docTypeInvoiceSearch.getValue() != null)
				docTypeInvoiceId = (Integer)docTypeInvoiceSearch.getValue();
		}
		data = getInvoiceData(multiCurrency.isSelected(), dateField.getValue(), invoiceTable, docTypeFilter.isSelected(), docTypeInvoiceId);
		//	End Jorge Colmenarez
		columnNames = getInvoiceColumnNames(multiCurrency.isSelected());
		
		invoiceTable.clear();
		
		//  Remove previous listeners
		invoiceTable.getModel().removeTableModelListener(this);
		
		//  Set Model
		ListModelTable modelI = new ListModelTable(data);
		modelI.addTableModelListener(this);
		invoiceTable.setData(modelI, columnNames);
		setInvoiceColumnClass(invoiceTable, multiCurrency.isSelected());
		//
		
		calculate(multiCurrency.isSelected());
		
		//  Calculate Totals
		calculate();
		
		statusBar.getChildren().clear();
	}   //  loadBPartner
	
	/**
	 *  Load Business Partner Info
	 *  - Payments
	 *  - Invoices
	 */
	private void loadBPartner2 ()
	{
		checkBPartner2();
		//	Added by Jorge Colmenarez, 2024-01-18 11:04
		//	Support for filter by Payment DocType
		int docTypePaymentId = 0;
		if (filterbyDocType) {
			if(docTypePaymentSearch.getValue() != null)
				docTypePaymentId = (Integer)docTypePaymentSearch.getValue();
		}
		Vector<Vector<Object>> data = getPaymentData(multiCurrency.isSelected(), dateField.getValue(), paymentTable, docTypeFilter.isSelected(), docTypePaymentId);
		//	End Jorge Colmenarez
		Vector<String> columnNames = getPaymentColumnNames(multiCurrency.isSelected());
		
		paymentTable.clear();
		
		//  Remove previous listeners
		paymentTable.getModel().removeTableModelListener(this);
		
		//  Set Model
		ListModelTable modelP = new ListModelTable(data);
		modelP.addTableModelListener(this);
		paymentTable.setData(modelP, columnNames);
		setPaymentColumnClass(paymentTable, multiCurrency.isSelected());
		//

		//	Added by Jorge Colmenarez, 2024-01-18 11:04
		//	Support for filter by Invoice DocType
		int docTypeInvoiceId = 0;
		if (filterbyDocType) {
			if(docTypeInvoiceSearch.getValue() != null)
				docTypeInvoiceId = (Integer)docTypeInvoiceSearch.getValue();
		}
		data = getInvoiceData(multiCurrency.isSelected(), dateField.getValue(), invoiceTable, docTypeFilter.isSelected(), docTypeInvoiceId);
		//	End Jorge Colmenarez
		columnNames = getInvoiceColumnNames(multiCurrency.isSelected());
		
		invoiceTable.clear();
		
		//  Remove previous listeners
		invoiceTable.getModel().removeTableModelListener(this);
		
		//  Set Model
		ListModelTable modelI = new ListModelTable(data);
		modelI.addTableModelListener(this);
		invoiceTable.setData(modelI, columnNames);
		setInvoiceColumnClass(invoiceTable, multiCurrency.isSelected());
		//
		
		calculate(multiCurrency.isSelected());
		
		//  Calculate Totals
		calculate();
		
		statusBar.getChildren().clear();
	}   //  loadBPartner
	
	
	
	public void calculate()
	{
		allocDate = null;
		
		paymentInfo.setText(calculatePayment(paymentTable, multiCurrency.isSelected()));
		invoiceInfo.setText(calculateInvoice(invoiceTable, multiCurrency.isSelected()));
		
		//	Set AllocationDate
		if (allocDate != null)
			dateField.setValue(allocDate);
		//  Set Allocation Currency
		allocCurrencyLabel.setText(currencyPick.getDisplay());
		//  Difference
		totalDiff = totalPay.subtract(totalInv);
		differenceField.setText(format.format(totalDiff));		

		setAllocateButton();
	}
	
	/**************************************************************************
	 *  Save Data
	 */
	private MFTUAllocationHeader saveData()
	{
		if (m_AD_Org_ID > 0)
			Env.setContext(Env.getCtx(), form.getWindowNo(), "AD_Org_ID", m_AD_Org_ID);
		else
			Env.setContext(Env.getCtx(), form.getWindowNo(), "AD_Org_ID", "");
		try
		{
			final MFTUAllocationHeader[] allocation = new MFTUAllocationHeader[1];
			Trx.run(new TrxRunnable() 
			{
				public void run(String trxName)
				{
					statusBar.getChildren().clear();
					allocation[0] = saveData(form.getWindowNo(), dateField.getValue(), dateAcctField.getValue(), paymentTable, invoiceTable, trxName);
					if(showActivityAndCostCenter) {
						activityPick.setSelectedIndex(0);
						costcenterPick.setSelectedIndex(0);
					}
				}
			});
			
			return allocation[0];
		}
		catch (Exception e)
		{
			FDialog.error(form.getWindowNo(), form, "Error", e.getLocalizedMessage());
			return null;
		}
	}   //  saveData
	
	/**
	 * Called by org.adempiere.webui.panel.ADForm.openForm(int)
	 * @return
	 */
	public ADForm getForm()
	{
		return form;
	}
	
}
