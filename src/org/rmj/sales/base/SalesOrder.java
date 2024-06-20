/**
 * @author  Michael Cuison
 */
package org.rmj.sales.base;

import com.mysql.jdbc.Connection;
import com.sun.javafx.image.impl.IntArgb;
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
import org.rmj.appdriver.constants.TransactionStatus;
import org.rmj.payment.agent.XMORMaster;
import org.rmj.sales.pojo.UnitSalesOrderDetail;
import org.rmj.sales.pojo.UnitSalesOrderMaster;

public class SalesOrder implements GTransaction{
    @Override
    public UnitSalesOrderMaster newTransaction() {
        UnitSalesOrderMaster loObj = new UnitSalesOrderMaster();
        Connection loConn = null;
        loConn = setConnection();
        
        loObj.setTransNo(MiscUtil.getNextCode(loObj.getTable(), "sTransNox", true, loConn, psBranchCd));
        loObj.setVATRate(1.12);
        
        //init detail
        poDetail = new ArrayList<>();
        
        return loObj;
    }

    @Override
    public UnitSalesOrderMaster loadTransaction(String fsTransNox) {
        UnitSalesOrderMaster loObject = new UnitSalesOrderMaster();
        
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
    public UnitSalesOrderMaster saveUpdate(Object foEntity, String fsTransNox) {
        String lsSQL = "";
        
        UnitSalesOrderMaster loOldEnt = null;
        UnitSalesOrderMaster loNewEnt = null;
        UnitSalesOrderMaster loResult = null;
        
        // Check for the value of foEntity
        if (!(foEntity instanceof UnitSalesOrderMaster)) {
            setErrMsg("Invalid Entity Passed as Parameter");
            return loResult;
        }
        
        // Typecast the Entity to this object
        loNewEnt = (UnitSalesOrderMaster) foEntity;
        
        
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
        
        if (!pbWithParent) poGRider.beginTrans();
        
        // Generate the SQL Statement
        if (fsTransNox.equals("")){
            try {
                Connection loConn = null;
                loConn = setConnection();
                
                String lsTransNox = MiscUtil.getNextCode(loNewEnt.getTable(), "sTransNox", false, loConn, psBranchCd);
                
                //save detail first
                saveDetail(lsTransNox, true);
                
                loNewEnt.setEntryNo(ItemCount());
                loNewEnt.setDateModified(poGRider.getServerDate());
                
                loNewEnt.setTransNo(lsTransNox);
                loNewEnt.setPreparedBy(poCrypt.encrypt(psUserIDxx));
                if (!pbWithParent) MiscUtil.close(loConn);
                
                //Generate the SQL Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt);
            } catch (SQLException ex) {
                Logger.getLogger(SalesOrder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }else{
            try {
                //Load previous transaction
                loOldEnt = loadTransaction(fsTransNox);
                
                //save detail first
                saveDetail(fsTransNox, true);
                
                loNewEnt.setEntryNo(ItemCount());
                loNewEnt.setDateModified(poGRider.getServerDate());
                
                //Generate the Update Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, (GEntity) loOldEnt, "sTransNox = " + SQLUtil.toSQL(loNewEnt.getTransNo()));
            } catch (SQLException ex) {
                Logger.getLogger(SalesOrder.class.getName()).log(Level.SEVERE, null, ex);
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
        UnitSalesOrderMaster loObject = loadTransaction(fsTransNox);
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
        UnitSalesOrderMaster loObject = loadTransaction(fsTransNox);
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
        
        double lnSubTotal = Double.valueOf(loObject.getTranTotal().toString())  + Double.valueOf(loObject.getFreightCharge().toString());
        double lnVATRatex = Double.valueOf(loObject.getVATRate().toString());
        double lnVATExcls = lnSubTotal / lnVATRatex;
        double lnDiscount = lnVATExcls * Double.valueOf(loObject.getDiscount().toString());
        double lnAddDiscx = Double.valueOf(loObject.getAddtlDiscount().toString()) / lnVATRatex;
        //double lnVATSales = lnVATExcls - (lnDiscount + lnAddDiscx);
        //double lnVATAmntx = lnVATSales * 0.12;
        
        //SAVE RECEIPT
        XMORMaster instance = new XMORMaster(poGRider, poGRider.getBranchCode(), true);
        instance.newTransaction();
        instance.setClientID(loObject.getClientID());
        
        instance.setTranTotl(Double.valueOf(loObject.getTranTotal().toString()));
        instance.setFreightCharge(Double.valueOf(loObject.getFreightCharge().toString()));
        instance.setDiscount(lnDiscount);
        instance.setAddtnlDiscount(lnAddDiscx);
        //instance.setVATExclusive(lnVATExcls);
        //instance.setVATableSales(lnVATSales);
        //instance.setVATableAmntx(lnVATAmntx);
        
        instance.setMaster("sSourceCd", "SL");
        instance.setMaster("sSourceNo", loObject.getTransNo());
        if(instance.showReceipt()){
            //UPDATE THE TRANSACTION STATUS
            String lsSQL = "UPDATE " + loObject.getTable() + 
                            " SET  nAmtPaidx = " + SQLUtil.toSQL(instance.getMaster("nCashAmtx")) +
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
        UnitSalesOrderMaster loObject = loadTransaction(fsTransNox);
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
                            ", sPostedxx = " + SQLUtil.toSQL(poCrypt.encrypt(psUserIDxx)) +
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
        UnitSalesOrderMaster loObject = loadTransaction(fsTransNox);
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
        UnitSalesOrderMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
               
        if (loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
            loObject.getTranStatus().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to cancel cancelled/posted/voided transaction.");
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
                    ", dExpected" +
                    ", sClientID" +
                    ", sReferNox" +
                    ", sRemarksx" +
                    ", nTranTotl" +
                    ", nVATRatex" +
                    ", nDiscount" +
                    ", nAddDiscx" +
                    ", nFreightx" +
                    ", nAmtPaidx" +
                    ", nForCredt" +
                    ", nCredtAmt" +
                    ", dDueDatex" +
                    ", sTermCode" +
                    ", sSourceCd" +
                    ", sSourceNo" +
                    ", nEntryNox" +
                    ", sInvTypCd" +
                    ", cTranStat" +
                    ", sPrepared" +
                    ", dPrepared" +
                    ", sApproved" +
                    ", dApproved" +
                    ", sPostedxx" +
                    ", dPostedxx" +
                    ", sModified" +
                    ", dModified" +
                " FROM Sales_Order_Master";
    }
    
    //Added detail methods
    public int ItemCount(){
        return poDetail.size();
    }
    
    public boolean addDetail() {
        UnitSalesOrderDetail loDetail = new UnitSalesOrderDetail();
        
        poDetail.add(loDetail);
        return true;
    }

    public boolean deleteDetail(int fnEntryNox) {
        poDetail.remove(fnEntryNox);
        return true;
    }
    
    public void setDetail(int fnRow, int fnCol, Object foData){
        switch (fnCol){
            case 5: //nUnitPrce
            case 6: //nDiscount
            case 7: //nAddDiscx
                if (foData instanceof Number){
                    poDetail.get(fnRow).setValue(fnCol, foData);
                    System.out.println(poDetail.get(fnRow).getValue(fnCol));
                }else poDetail.get(fnRow).setValue(fnCol, 0);
                break;
            case 4: //nQuantity
            case 9: //nApproved
            case 10: //nIssuedxx
            case 11: //nCancelld
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
            case "nUnitPrce":
            case "nDiscount":
            case "nAddDiscx":
                if (foData instanceof Number){
                    poDetail.get(fnRow).setValue(fsCol, foData);
                }else poDetail.get(fnRow).setValue(fsCol, 0);
                break;
            case "nQuantity":
            case "nApproved":
            case "nIssuedxx":
            case "nCancelld":
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
    
    private boolean saveDetail(String fsTransNox, boolean fbNewRecord) throws SQLException{
        if (ItemCount() <= 0){
            setMessage("No transaction detail detected.");
            return false;
        }
        
        UnitSalesOrderDetail loDetail;
        UnitSalesOrderDetail loOldDet;
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
                    return true;
                }
            }
            
            poDetail.get(lnCtr).setTransNo(fsTransNox);
            poDetail.get(lnCtr).setEntryNo(lnCtr + 1);
            poDetail.get(lnCtr).setDateModified(poGRider.getServerDate());
            
            if (fbNewRecord){
                //Generate the SQL Statement
                lsSQL = MiscUtil.makeSQL((GEntity) poDetail.get(lnCtr));
            }else{
                //Load previous transaction
                loOldDet = loadTransDetail(fsTransNox, lnCtr + 1);
            
                //Generate the Update Statement
                lsSQL = MiscUtil.makeSQL((GEntity) poDetail.get(lnCtr), (GEntity) loOldDet, 
                                            "sTransNox = " + SQLUtil.toSQL(poDetail.get(lnCtr).getTransNo()) + 
                                                " AND nEntryNox = " + poDetail.get(lnCtr).getEntryNo());
            }
            
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
        
        //check if the new detail is less than the original detail count
        int lnRow = loadTransDetail(fsTransNox).size();
        if (lnCtr < lnRow -1){
            for (lnCtr = lnCtr + 1; lnCtr <= lnRow; lnCtr++){
                lsSQL = "DELETE FROM Sales_Order_Detail" + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(fsTransNox) + 
                            " AND nEntryNox = " + lnCtr;
                
                if(poGRider.executeQuery(lsSQL, pxeDetTable, "", "") == 0){
                    if(!poGRider.getErrMsg().isEmpty()) setErrMsg(poGRider.getErrMsg());
                }else {
                    setMessage("No record updated");
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private UnitSalesOrderDetail loadTransDetail(String fsTransNox, int fnEntryNox) throws SQLException{
        UnitSalesOrderDetail loObj = null;
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)) + 
                                                    " AND nEntryNox = " + fnEntryNox);
        
        if (!loRS.next()){
            setMessage("No Record Found");
        }else{
            //load each column to the entity
            loObj = new UnitSalesOrderDetail();
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                loObj.setValue(lnCol, loRS.getObject(lnCol));
            }
        }      
        return loObj;
    }
    
    private ArrayList<UnitSalesOrderDetail> loadTransDetail(String fsTransNox) throws SQLException{
        UnitSalesOrderDetail loOcc = null;
        Connection loConn = null;
        loConn = setConnection();
        
        ArrayList<UnitSalesOrderDetail> loDetail = new ArrayList<>();
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        System.out.println(MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        if (MiscUtil.RecordCount(loRS) <= 0) return loDetail;
        
        loRS.beforeFirst();
        while(loRS.next()){
            loOcc = new UnitSalesOrderDetail();
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
                    ", sStockIDx" + 
                    ", nQuantity" + 
                    ", nUnitPrce" + 
                    ", nDiscount" + 
                    ", nAddDiscx" + 
                    ", cClassify" + 
                    ", nApproved" + 
                    ", nIssuedxx" + 
                    ", nCancelld" + 
                    ", sNotesxxx" + 
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
    
    //Member Variables
    private GRider poGRider = null;
    private String psUserIDxx = "";
    private String psBranchCd = "";
    private String psWarnMsg = "";
    private String psErrMsgx = "";
    private boolean pbWithParent = false;
    private final GCrypt poCrypt = new GCrypt();
    
    private ArrayList<UnitSalesOrderDetail> poDetail;
    private final String pxeDetTable = "Sales_Order_Detail";
}