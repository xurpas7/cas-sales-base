/**
 * @author  Michael Cuison
 */
package org.rmj.sales.base;

import com.mysql.jdbc.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rmj.appdriver.GCrypt;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;
import org.rmj.appdriver.iface.GTransaction;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.constants.CRMEvent;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.TransactionStatus;
import org.rmj.cas.inventory.base.InventoryTrans;
import org.rmj.payment.agent.XMORMaster;
import org.rmj.payment.agent.constant.PaymentType;
import org.rmj.sales.pojo.UnitSalesDetail;
import org.rmj.sales.pojo.UnitSalesMaster;

public class Sales implements GTransaction{
    @Override
    public UnitSalesMaster newTransaction() {
        UnitSalesMaster loObj = new UnitSalesMaster();
        Connection loConn = null;
        loConn = setConnection();
        
        loObj.setTransNo(MiscUtil.getNextCode(loObj.getTable(), "sTransNox", true, loConn, poGRider.getBranchCode() + System.getProperty("pos.clt.trmnl.no")));
        loObj.setVATRate(1.12);
        
        //init detail
        poDetail = new ArrayList<>();
        
        return loObj;
    }

    @Override
    public UnitSalesMaster loadTransaction(String fsTransNox) {
        UnitSalesMaster loObject = new UnitSalesMaster();
        
        Connection loConn = null;
        loConn = setConnection();   
        
        String lsSQL = MiscUtil.addCondition(getSQ_Master(), "sTransNox = " + SQLUtil.toSQL(fsTransNox));
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        
        try {
            if (!loRS.next()){
                setMessage("No Record Found");
            }else{
                //load each column to the entity
                for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                    loObject.setValue(lnCol, loRS.getObject(lnCol));
                }
                
                //load detail
                poDetail = loadTransDetail(fsTransNox);
            }              
        } catch (SQLException ex) {
            setErrMsg(ex.getMessage());
        } finally{
            MiscUtil.close(loRS);
            if (!pbWithParent) MiscUtil.close(loConn);
        }
        
        return loObject;
    }

    @Override
    public UnitSalesMaster saveUpdate(Object foEntity, String fsTransNox) {
        String lsSQL = "";
        
        UnitSalesMaster loOldEnt = null;
        UnitSalesMaster loNewEnt = null;
        UnitSalesMaster loResult = null;
        
        // Check for the value of foEntity
        if (!(foEntity instanceof UnitSalesMaster)) {
            setErrMsg("Invalid Entity Passed as Parameter");
            return loResult;
        }
        
        // Typecast the Entity to this object
        loOldEnt = (UnitSalesMaster) foEntity; 
        loNewEnt = (UnitSalesMaster) foEntity;
        
        // Test if entry is ok
        if (loNewEnt.getClientID()== null || loNewEnt.getClientID().isEmpty()){
            setMessage("Invalid client detected.");
            return loResult;
        }
        
        if (loNewEnt.getDateTransact()== null){
            setMessage("Invalid transaction date detected.");
            return loResult;
        }
        
        if (loNewEnt.getInvTypeCode()== null || loNewEnt.getInvTypeCode().isEmpty()){
            setMessage("Invalid inventory type detected.");
            return loResult;
        }
        
        //check 0 quantity order detail
        for (int lnCtr = 0; lnCtr <= poDetail.size()-1; lnCtr++){
            if (poDetail.get(lnCtr).getQuantity() <= 0 || 
                    poDetail.get(lnCtr).getStockID().isEmpty()){
                
                if (poDetail.size() > 1 && lnCtr == poDetail.size()-1){
                    deleteDetail(lnCtr);
                    break;
                }
                
                setMessage("Invalid detail info detected...");
                return loResult;
            }
        }
        
        if (!pbWithParent) poGRider.beginTrans();
        
        // Generate the SQL Statement
        if (fsTransNox.equals("")){
            //new record
            try {
                Connection loConn = null;
                loConn = setConnection();
                 
                String lsTransNox = MiscUtil.getNextCode(loNewEnt.getTable(), "sTransNox", true, loConn, psBranchCd + System.getProperty("pos.clt.trmnl.no"));
                
                //save detail first
                saveDetail(lsTransNox);
                
                loNewEnt.setEntryNo(ItemCount());
                
                loNewEnt.setTransNo(lsTransNox);
                loNewEnt.setPreparedDate(poGRider.getServerDate());
                loNewEnt.setPreparedBy(psUserIDxx);
                
                loNewEnt.setModifiedBy(psUserIDxx);
                loNewEnt.setDateModified(poGRider.getServerDate());
                
                if (!pbWithParent) MiscUtil.close(loConn);
                
                //Generate the SQL Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt);
            } catch (SQLException ex) {
                Logger.getLogger(Sales.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else{
            try {
                deleteDetail(fsTransNox);
                saveDetail(fsTransNox);
               
                loNewEnt.setEntryNo(ItemCount());
                loNewEnt.setModifiedBy(psUserIDxx);
                loNewEnt.setDateModified(poGRider.getServerDate());
                
                loOldEnt = (UnitSalesMaster) loadTransaction(fsTransNox);
                
                //Generate the SQL Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, (GEntity) loOldEnt, "sTransNox = " + SQLUtil.toSQL(fsTransNox));
            } catch (SQLException ex) {
                Logger.getLogger(Sales.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //No changes have been made
        if (lsSQL.equals("")){
            setMessage("Record is not updated");
            return loResult;
        }
        
        if(poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0){
            if(!poGRider.getErrMsg().isEmpty())
                setErrMsg(poGRider.getErrMsg());
            else
            setMessage("No record updated");
        } else loResult = loNewEnt;
        
        if (!pbWithParent) {
            if (!getErrMsg().isEmpty()){
                poGRider.rollbackTrans();
            } else poGRider.commitTrans();
        }        
        
        return loResult;
    }

    @Override
    public boolean deleteTransaction(String fsTransNox) {
        UnitSalesMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        String lsSQL = "DELETE FROM " + loObject.getTable() + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(fsTransNox);
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        //delete detail rows
        lsSQL = "DELETE FROM " + pxeDetTable +
                " WHERE sTransNox = " + SQLUtil.toSQL(fsTransNox);
        
        if (poGRider.executeQuery(lsSQL, pxeDetTable, "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        
        return lbResult;
    }

    @Override
    public boolean closeTransaction(String fsTransNox) {
        UnitSalesMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (!loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_OPEN)){
            setMessage("Unable to close closed/cancelled/posted/voided transaction.");
            return lbResult;
        }
        
        if (!pbWithParent) poGRider.beginTrans();
        
        //SAVE RECEIPT
        XMORMaster instance = new XMORMaster(poGRider, poGRider.getBranchCode(), true);
        instance.newTransaction();
        instance.setClientID(loObject.getClientID());
        
        instance.setVATRate(Double.parseDouble(loObject.getVATRate().toString()));
        instance.setTranTotl(Double.parseDouble(loObject.getTranTotal().toString()));
        instance.setFreightCharge(Double.parseDouble(loObject.getFreightCharge().toString()));
        instance.setDiscount(Double.parseDouble(loObject.getDiscount().toString()));
        instance.setAddtnlDiscount(Double.parseDouble(loObject.getAddtlDiscount().toString()));
        
        instance.setMaster("sSourceCd", "SL");
        instance.setMaster("sSourceNo", loObject.getTransNo());
        instance.setMaster("sCashierx", psUserIDxx);
        instance.setMaster("cTranStat", TransactionStatus.STATE_CLOSED);
        if(instance.showReceipt()){
            //UPDATE THE TRANSACTION STATUS
            String lsSQL = "UPDATE " + loObject.getTable() + 
                            " SET  nAmtPaidx = " + instance.getAmountPaid()+
                                ", cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CLOSED) + 
                                ", sApproved = " + SQLUtil.toSQL(psUserIDxx) +
                                ", dApproved = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                                ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                            " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNo());
        
            if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
                if (!poGRider.getErrMsg().isEmpty()){
                    setErrMsg(poGRider.getErrMsg());
                } else setErrMsg("No record deleted.");  
            } else lbResult = true;
            
            lbResult = saveInvTrans();
        }
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        
        return lbResult;
    }

    @Override
    public boolean postTransaction(String fsTransNox) {
        UnitSalesMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to post cancelled/posted/voided transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_POSTED) + 
                            ", sPostedxx = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dPostedxx = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNo());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }

    @Override
    public boolean voidTransaction(String fsTransNox) {
        UnitSalesMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_POSTED)){
            setMessage("Unable to void posted transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNo());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }

    @Override
    public boolean cancelTransaction(String fsTransNox) {
        UnitSalesMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
               
        if (!loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_CLOSED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            
            setMessage("Unable to cancel open/posted/voided transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CANCELLED) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNo());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record updated.");  
        } else {
            lsSQL = "SELECT" +
                        "  sTransNox" +
                        ", sORNumber" +
                    " FROM Receipt_Master" + 
                    " WHERE sSourceCd = 'SL'" +
                        " AND sSourceNo = " + SQLUtil.toSQL(loObject.getTransNo());
                                
            ResultSet loRS = poGRider.executeQuery(lsSQL);
            
            try {
                if (loRS.next()){
                    CommonUtils.createEventLog(poGRider, poGRider.getBranchCode() + System.getProperty("pos.clt.trmnl.no"), CRMEvent.CANCELLED_INVOICE, "OR No.: " + loRS.getString("sORNumber"), System.getProperty("pos.clt.crm.no"));

                    String lsReceiptx = loRS.getString("sTransNox");
                    
                    lsSQL = "UPDATE Receipt_Master SET" +
                                "  cTranStat = '3'" +
                                ", cPrintedx = '0'" +
                                ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                            " WHERE sTransNox = " + SQLUtil.toSQL(lsReceiptx);
                    
                    //cancel the receipt / cash payment
                    if (poGRider.executeQuery(lsSQL, "Receipt_Master", "", "") == 0){
                        if (!poGRider.getErrMsg().isEmpty()){
                            setErrMsg(poGRider.getErrMsg());
                        } else setErrMsg("No record updated.");  
                    } else { //cancel the other payments
                        lsSQL = "SELECT sTransNox FROM Sales_Payment" + 
                                " WHERE sSourceCd = 'ORec'" + 
                                    " AND sSourceNo = " + SQLUtil.toSQL(lsReceiptx) + 
                                    " AND cPaymForm = " + SQLUtil.toSQL(PaymentType.CREDIT_CARD);
                        loRS = poGRider.executeQuery(lsSQL);
                        if (loRS.next()){ //cancel credit card payment
                            lsSQL = "UPDATE Credit_Card_Trans SET" +
                                        "  cTranStat = '3'" +
                                        ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                                    " WHERE sSourceCd = 'SlPy'" + 
                                        " AND sSourceNo = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                            poGRider.executeQuery(lsSQL, "Credit_Card_Trans", "", "");
                        }
                        
                        lsSQL = "SELECT sTransNox FROM Sales_Payment" + 
                                " WHERE sSourceCd = 'ORec'" + 
                                    " AND sSourceNo = " + SQLUtil.toSQL(lsReceiptx) + 
                                    " AND cPaymForm = " + SQLUtil.toSQL(PaymentType.CHECK);
                        loRS = poGRider.executeQuery(lsSQL);
                        if (loRS.next()){ //cancel credit card payment
                            lsSQL = "UPDATE Check_Payment_Trans SET" +
                                        "  cTranStat = '3'" +
                                        ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                                    " WHERE sSourceCd = 'SlPy'" + 
                                        " AND sSourceNo = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                            poGRider.executeQuery(lsSQL, "Check_Payment_Trans", "", "");
                        }
                        
                        lsSQL = "SELECT sTransNox FROM Sales_Payment" + 
                                " WHERE sSourceCd = 'ORec'" + 
                                    " AND sSourceNo = " + SQLUtil.toSQL(lsReceiptx) + 
                                    " AND cPaymForm = " + SQLUtil.toSQL(PaymentType.GIFT_CERTIFICATE);
                        loRS = poGRider.executeQuery(lsSQL);
                        if (loRS.next()){ //cancel credit card payment
                            lsSQL = "UPDATE Gift_Certificate_Trans SET" +
                                        "  cTranStat = '3'" +
                                        ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                                    " WHERE sSourceCd = 'SlPy'" + 
                                        " AND sSourceNo = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                            poGRider.executeQuery(lsSQL, "Gift_Certificate_Trans", "", "");
                        }
                        
                        lsSQL = "SELECT sTransNox FROM Sales_Payment" + 
                                " WHERE sSourceCd = 'ORec'" + 
                                    " AND sSourceNo = " + SQLUtil.toSQL(lsReceiptx) + 
                                    " AND cPaymForm = " + SQLUtil.toSQL(PaymentType.FINANCER);
                        loRS = poGRider.executeQuery(lsSQL);
                        if (loRS.next()){ //cancel finance payment
                            lsSQL = "UPDATE Financer_Trans SET" +
                                        "  cTranStat = '3'" +
                                        ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                                    " WHERE sSourceCd = 'SlPy'" + 
                                        " AND sSourceNo = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                            poGRider.executeQuery(lsSQL, "Financer_Trans", "", "");
                        }
                        
                        lbResult = true;
                        
                        CommonUtils.createEventLog(poGRider, poGRider.getBranchCode() + System.getProperty("pos.clt.trmnl.no"), CRMEvent.ACTION_ALLOWED, "", System.getProperty("pos.clt.crm.no"));
                    }
                } else
                    CommonUtils.createEventLog(poGRider, poGRider.getBranchCode() + System.getProperty("pos.clt.trmnl.no"), CRMEvent.ACTION_DENIED, "", System.getProperty("pos.clt.crm.no"));
            } catch (SQLException ex) {
                Logger.getLogger(Sales.class.getName()).log(Level.SEVERE, null, ex);
                setErrMsg(ex.getMessage());
            }
        }     
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }

    @Override
    public String getMessage() {
        return psWarnMsg;
    }

    @Override
    public void setMessage(String fsMessage) {
        this.psWarnMsg = fsMessage;
    }

    @Override
    public String getErrMsg() {
        return psErrMsgx;
    }

    @Override
    public void setErrMsg(String fsErrMsg) {
        this.psErrMsgx = fsErrMsg;
    }

    @Override
    public void setBranch(String foBranchCD) {
        this.psBranchCd = foBranchCD;
    }

    @Override
    public void setWithParent(boolean fbWithParent) {
        this.pbWithParent = fbWithParent;
    }

    @Override
    public String getSQ_Master() {
        return "SELECT" +
                    "  sTransNox" +
                    ", sBranchCd" +
                    ", dTransact" +
                    ", sClientID" +
                    ", sReferNox" +
                    ", sRemarksx" +
                    ", sSalesman" +
                    ", cPaymForm" +
                    ", nTranTotl" +
                    ", nVATRatex" +
                    ", nDiscount" +
                    ", nAddDiscx" +
                    ", nFreightx" +
                    ", nAmtPaidx" +
                    ", sTermCode" +
                    ", dDueDatex" +
                    ", sSourceCd" +
                    ", sSourceNo" +
                    ", nEntryNox" +
                    ", sInvTypCd" +
                    ", cTranStat" +
                    ", nPostStat" +
                    ", sPrepared" +
                    ", dPrepared" +
                    ", sApproved" +
                    ", dApproved" +
                    ", sAprvCode" +
                    ", sReleased" +
                    ", dReleased" +
                    ", sPostedxx" +
                    ", dPostedxx" +
                    ", sModified" +
                    ", dModified" + 
                " FROM Sales_Master" + 
                " WHERE sTransNox LIKE " + SQLUtil.toSQL(psBranchCd + System.getProperty("pos.clt.trmnl.no") + "%");
    }
    
    //Added detail methods
    public int ItemCount(){
        return poDetail.size();
    }
    
    public boolean addDetail() {        
        if (poDetail.isEmpty()){
            poDetail.add(new UnitSalesDetail());
        } else {
            if (!poDetail.get(ItemCount()-1).getStockID().equals("") &&
                    poDetail.get(ItemCount() -1).getQuantity() != 0){
                poDetail.add(new UnitSalesDetail());
            }
        }
        
        return true;
    }

    public boolean deleteDetail(int fnEntryNox) {
        poDetail.remove(fnEntryNox);
        
        if (poDetail.isEmpty()) return addDetail();
        return true;
    }
    
    public void setDetail(int fnRow, int fnCol, Object foData){
        switch (fnCol){
            case 6: //nInvCostx
            case 7: //nUnitPrce
            case 8: //nDiscount
            case 9: //nAddDiscx
                if (foData instanceof Number){
                    poDetail.get(fnRow).setValue(fnCol, foData);
                    System.out.println(poDetail.get(fnRow).getValue(fnCol));
                }else poDetail.get(fnRow).setValue(fnCol, 0);
                break;
            case 5: //nQuantity
                if (foData instanceof Integer){
                    poDetail.get(fnRow).setValue(fnCol, foData);
                }else poDetail.get(fnRow).setValue(fnCol, 0);
                break;
            default:
                poDetail.get(fnRow).setValue(fnCol, foData);
        }
    }
    public void setDetail(int fnRow, String fsCol, Object foData){
        switch(fsCol){
            case "nInvCostx":
            case "nUnitPrce":
            case "nDiscount":
            case "nAddDiscx":
                if (foData instanceof Number){
                    poDetail.get(fnRow).setValue(fsCol, foData);
                }else poDetail.get(fnRow).setValue(fsCol, 0);
                break;
            case "nQuantity":
                if (foData instanceof Integer){
                    poDetail.get(fnRow).setValue(fsCol, foData);
                }else poDetail.get(fnRow).setValue(fsCol, 0);
                break;
            default:
                poDetail.get(fnRow).setValue(fsCol, foData);
        }
    }
    
    public Object getDetail(int fnRow, int fnCol){return poDetail.get(fnRow).getValue(fnCol);}
    public Object getDetail(int fnRow, String fsCol){return poDetail.get(fnRow).getValue(fsCol);}
    
    private boolean deleteDetail(String fsTransNox){
        if (ItemCount() == 0) return true;
        
        String lsSQL = "DELETE FROM " + pxeDetTable + " WHERE sTransNox LIKE " + SQLUtil.toSQL(fsTransNox);
        
        return poGRider.executeQuery(lsSQL, pxeDetTable, "", "") != 0;
    }
    
    private boolean saveDetail(String fsTransNox) throws SQLException{
        if (ItemCount() <= 0){
            setMessage("No transaction detail detected.");
            return false;
        }
        
        UnitSalesDetail loDetail;
        UnitSalesDetail loOldDet;
        int lnCtr;
        String lsSQL;        
        
        for (lnCtr = 0; lnCtr <= ItemCount() -1; lnCtr++){
            if (lnCtr == 0){
                if (poDetail.get(lnCtr).getStockID() == null || poDetail.get(lnCtr).getStockID().isEmpty()){
                    setMessage("Invalid ordered parts detected.");
                    return false;
                }
            }else {
                if (poDetail.get(lnCtr).getStockID() == null || poDetail.get(lnCtr).getStockID().isEmpty()){ 
                    poDetail.remove(lnCtr);
                    break;
                }
            }
            
            poDetail.get(lnCtr).setTransNo(fsTransNox);
            poDetail.get(lnCtr).setEntryNo(lnCtr + 1);
            poDetail.get(lnCtr).setDateModified(poGRider.getServerDate());
            
            lsSQL = MiscUtil.makeSQL((GEntity) poDetail.get(lnCtr));
            
            if (!lsSQL.equals("")){
                if(poGRider.executeQuery(lsSQL, pxeDetTable, "", "") == 0){
                    if(!poGRider.getErrMsg().isEmpty()){ 
                        setErrMsg(poGRider.getErrMsg());
                        return false;
                    }
                }else {
                    setMessage("No record updated");
                }
            }
        }    
        return true;
    }
    
    private UnitSalesDetail loadTransDetail(String fsTransNox, int fnEntryNox) throws SQLException{
        UnitSalesDetail loObj = null;
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)) + 
                                                    " AND nEntryNox = " + fnEntryNox);
        
        if (!loRS.next()){
            setMessage("No Record Found");
        }else{
            //load each column to the entity
            loObj = new UnitSalesDetail();
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                loObj.setValue(lnCol, loRS.getObject(lnCol));
            }
        }      
        return loObj;
    }
    
    private ArrayList<UnitSalesDetail> loadTransDetail(String fsTransNox) throws SQLException{
        UnitSalesDetail loOcc = null;
        Connection loConn = null;
        loConn = setConnection();
        
        ArrayList<UnitSalesDetail> loDetail = new ArrayList<>();
        
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        
        
        if (MiscUtil.RecordCount(loRS) <= 0) return loDetail;
        
        loRS.beforeFirst();
        while(loRS.next()){
            loOcc = new UnitSalesDetail();
            //load each column to the entity
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                loOcc.setValue(lnCol, loRS.getObject(lnCol));
            }

            loDetail.add(loOcc);
         }
        
        return loDetail;
    }
    
    private String getSQ_Detail(){
        return "SELECT" +
                    "  sTransNox" +
                    ", nEntryNox" +
                    ", sOrderNox" +
                    ", sStockIDx" +
                    ", nQuantity" +
                    ", nInvCostx" +
                    ", nUnitPrce" +
                    ", nDiscount" +
                    ", nAddDiscx" +
                    ", sSerialID" +
                    ", cNewStock" +
                    ", sInsTypID" +
                    ", nInsAmtxx" +
                    ", sWarrntNo" +
                    ", cUnitForm" +
                    ", sNotesxxx" +
                    ", cDetailxx" +
                    ", cPromoItm" +
                    ", cComboItm" +
                    ", dModified" +
                " FROM " + pxeDetTable +
                " ORDER BY nEntryNox";
    }
    
    //Added methods
    public void setGRider(GRider foGRider){
        this.poGRider = foGRider;
        this.psUserIDxx = foGRider.getUserID();
        
        if (psBranchCd.isEmpty()) psBranchCd = foGRider.getBranchCode();
        
        poDetail = new ArrayList<>();
    }
    
    public void setUserID(String fsUserID){
        this.psUserIDxx  = fsUserID;
    }
    
    private Connection setConnection(){
        Connection foConn;
        
        if (pbWithParent){
            foConn = (Connection) poGRider.getConnection();
            if (foConn == null) foConn = (Connection) poGRider.doConnect();
        }else foConn = (Connection) poGRider.doConnect();
        
        return foConn;
    }
    
    private boolean saveInvTrans(){
        String lsSQL = "";
        ResultSet loRS = null;
        int lnCtr;
        
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
                
        /*---------------------------------------------------------------------------------
         *   Save inventory trans of the items
         *---------------------------------------------------------------------------------*/
        loInvTrans.InitTransaction();
        for (lnCtr = 0; lnCtr <= poDetail.size() - 1; lnCtr ++){
            if (poDetail.get(lnCtr).getStockID().equals("")) break;
            
            lsSQL = "SELECT" +
                        "  nQtyOnHnd" +
                        ", nResvOrdr" +
                        ", nBackOrdr" +
                        ", nLedgerNo" +
                    " FROM Inv_Master" + 
                    " WHERE sStockIDx = " + SQLUtil.toSQL(poDetail.get(lnCtr).getStockID()) + 
                        " AND sBranchCd = " + SQLUtil.toSQL(psBranchCd);

            loRS = poGRider.executeQuery(lsSQL);
            
            loInvTrans.setDetail(lnCtr, "sStockIDx", poDetail.get(lnCtr).getStockID());
            loInvTrans.setDetail(lnCtr, "sReplacID", "");
            loInvTrans.setDetail(lnCtr, "nQuantity", poDetail.get(lnCtr).getQuantity());
                
            if (MiscUtil.RecordCount(loRS) == 0){
                loInvTrans.setDetail(lnCtr, "nQtyOnHnd", 0);
                loInvTrans.setDetail(lnCtr, "nResvOrdr", 0);
                loInvTrans.setDetail(lnCtr, "nBackOrdr", 0);
            } else{
                try {
                    loRS.first();
                    loInvTrans.setDetail(lnCtr, "nQtyOnHnd", loRS.getInt("nQtyOnHnd"));
                    loInvTrans.setDetail(lnCtr, "nResvOrdr", loRS.getInt("nResvOrdr"));
                    loInvTrans.setDetail(lnCtr, "nBackOrdr", loRS.getInt("nBackOrdr"));
                    loInvTrans.setDetail(lnCtr, "nLedgerNo", loRS.getInt("nLedgerNo"));
                } catch (SQLException e) {
                    setMessage("Please inform MIS Department.");
                    setErrMsg(e.getMessage());
                    return false;
                }
            }
        }
        
        if (!loInvTrans.Sales(poDetail.get(0).getTransNo(), poGRider.getServerDate(), EditMode.ADDNEW)){
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }
    
        return true;
    }
    
    //Member Variables
    private GRider poGRider = null;
    private String psUserIDxx = "";
    private String psBranchCd = "";
    private String psWarnMsg = "";
    private String psErrMsgx = "";
    private boolean pbWithParent = false;
    private final GCrypt poCrypt = new GCrypt();
    
    private ArrayList<UnitSalesDetail> poDetail;
    private final String pxeDetTable = "Sales_Detail";
}