/**
 * @author  Michael Cuison
 * 
 * @since 2018.09.20
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
import org.rmj.appdriver.constants.TransactionStatus;
import org.rmj.sales.pojo.UnitSalesReturnDetail;
import org.rmj.sales.pojo.UnitSalesReturnMaster;

public class SalesReturn implements GTransaction{
    @Override
    public UnitSalesReturnMaster newTransaction() {
        UnitSalesReturnMaster loObj = new UnitSalesReturnMaster();
        Connection loConn = null;
        loConn = setConnection();
        
        loObj.setTransNox(MiscUtil.getNextCode(loObj.getTable(), "sTransNox", true, loConn, psBranchCd));
        
        //init detail
        poDetail = new ArrayList<>();
        
        return loObj;
    }

    @Override
    public UnitSalesReturnMaster loadTransaction(String fsTransNox) {
        UnitSalesReturnMaster loObject = new UnitSalesReturnMaster();
        
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
    public UnitSalesReturnMaster saveUpdate(Object foEntity, String fsTransNox) {
        String lsSQL = "";
        
        UnitSalesReturnMaster loOldEnt = null;
        UnitSalesReturnMaster loNewEnt = null;
        UnitSalesReturnMaster loResult = null;
        
        // Check for the value of foEntity
        if (!(foEntity instanceof UnitSalesReturnMaster)) {
            setErrMsg("Invalid Entity Passed as Parameter");
            return loResult;
        }
        
        // Typecast the Entity to this object
        loNewEnt = (UnitSalesReturnMaster) foEntity;
        
        
        // Test if entry is ok
        if (loNewEnt.getClientID()== null || loNewEnt.getClientID().isEmpty()){
            setMessage("Invalid client detected.");
            return loResult;
        }
        
        if (loNewEnt.getTransact()== null){
            setMessage("Invalid transaction date detected.");
            return loResult;
        }
        
        if (loNewEnt.getInvTypCd()== null || loNewEnt.getInvTypCd().isEmpty()){
            setMessage("Invalid inventory type detected.");
            return loResult;
        }
        
        if (!pbWithParent) poGRider.beginTrans();
        
        // Generate the SQL Statement
        if (fsTransNox.equals("")){
            try {
                Connection loConn = null;
                loConn = setConnection();
                
                String lsTransNox = MiscUtil.getNextCode(loNewEnt.getTable(), "sTransNox", true, loConn, psBranchCd);
                
                //save detail first
                if (!saveDetail(lsTransNox, true)){
                    if (!pbWithParent) poGRider.rollbackTrans();
                    return loResult;
                } 
                
                loNewEnt.setEntryNox(ItemCount());
                
                loNewEnt.setTransNox(lsTransNox);
                loNewEnt.setPrepared(poGRider.getServerDate());
                loNewEnt.setPrepared(psUserIDxx);
                
                loNewEnt.setModified(psUserIDxx);
                loNewEnt.setModified(poGRider.getServerDate());
                
                if (!pbWithParent) MiscUtil.close(loConn);
                
                //Generate the SQL Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt);
            } catch (SQLException ex) {
                Logger.getLogger(SalesReturn.class.getName()).log(Level.SEVERE, null, ex);
            }
        }else{
            try {
                //Load previous transaction
                loOldEnt = loadTransaction(fsTransNox);
                
                //save detail first
                if (!saveDetail(fsTransNox, true)){
                    if (!pbWithParent) poGRider.rollbackTrans();
                    return loResult;
                } 
                
                loNewEnt.setEntryNox(ItemCount());
                loNewEnt.setModified(poGRider.getServerDate());
                
                //Generate the Update Statement
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, (GEntity) loOldEnt, "sTransNox = " + SQLUtil.toSQL(loNewEnt.getTransNox()));
            } catch (SQLException ex) {
                Logger.getLogger(SalesReturn.class.getName()).log(Level.SEVERE, null, ex);
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
        UnitSalesReturnMaster loObject = loadTransaction(fsTransNox);
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
        UnitSalesReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (!loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_OPEN)){
            setMessage("Unable to close closed/cancelled/posted/voided transaction.");
            return lbResult;
        }
        
        if (!pbWithParent) poGRider.beginTrans();
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CLOSED) + 
                    ", sApproved = " + SQLUtil.toSQL(psUserIDxx) +
                    ", dApproved = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                    ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());

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
    public boolean postTransaction(String fsTransNox) {
        UnitSalesReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
            loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
            loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to post cancelled/posted/voided transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_POSTED) + 
                            ", sPostedxx = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dPostedxx = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
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
        UnitSalesReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED)){
            setMessage("Unable to void posted transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
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
        UnitSalesReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
               
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
            loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
            loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to cancel cancelled/posted/voided transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CANCELLED) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
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
        return (MiscUtil.makeSelect(new UnitSalesReturnMaster()));
    }
    
    //Added detail methods
    public int ItemCount(){
        return poDetail.size();
    }
    
    public boolean addDetail() {
        UnitSalesReturnDetail loDetail = new UnitSalesReturnDetail();
        
        poDetail.add(loDetail);
        return true;
    }

    public boolean deleteDetail(int fnEntryNox) {
        poDetail.remove(fnEntryNox);
        return true;
    }
    
    public void setDetail(int fnRow, int fnCol, Object foData){
        switch (fnCol){
            case 5: //nInvCostx
            case 6: //nUnitPrce
                if (foData instanceof Number){
                    poDetail.get(fnRow).setValue(fnCol, foData);
                }else poDetail.get(fnRow).setValue(fnCol, 0);
                break;
            case 4: //nQuantity
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
    
    private boolean saveDetail(String fsTransNox, boolean fbNewRecord) throws SQLException{
        if (ItemCount() <= 0){
            setMessage("No transaction detail detected.");
            return false;
        }
        
        UnitSalesReturnDetail loDetail;
        UnitSalesReturnDetail loOldDet;
        int lnCtr;
        String lsSQL;
        
        for (lnCtr = 0; lnCtr <= ItemCount() -1; lnCtr++){
            if (lnCtr == 0){
                if (poDetail.get(lnCtr).getStockID() == null || poDetail.get(lnCtr).getStockID().isEmpty()){
                    setMessage("Invalid item detected.");
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
                lsSQL = "DELETE FROM Sales_Return_Detail" + 
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
    
    private UnitSalesReturnDetail loadTransDetail(String fsTransNox, int fnEntryNox) throws SQLException{
        UnitSalesReturnDetail loObj = null;
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)) + 
                                                    " AND nEntryNox = " + fnEntryNox);
        
        if (!loRS.next()){
            setMessage("No Record Found");
        }else{
            //load each column to the entity
            loObj = new UnitSalesReturnDetail();
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                loObj.setValue(lnCol, loRS.getObject(lnCol));
            }
        }      
        return loObj;
    }
    
    private ArrayList<UnitSalesReturnDetail> loadTransDetail(String fsTransNox) throws SQLException{
        UnitSalesReturnDetail loOcc = null;
        Connection loConn = null;
        loConn = setConnection();
        
        ArrayList<UnitSalesReturnDetail> loDetail = new ArrayList<>();
        
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        
        
        if (MiscUtil.RecordCount(loRS) <= 0) return loDetail;
        
        loRS.beforeFirst();
        while(loRS.next()){
            loOcc = new UnitSalesReturnDetail();
            //load each column to the entity
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                loOcc.setValue(lnCol, loRS.getObject(lnCol));
            }

            loDetail.add(loOcc);
         }
        
        return loDetail;
    }
    
    private String getSQ_Detail(){
        return (MiscUtil.makeSelect(new UnitSalesReturnDetail()));
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
    
    private ArrayList<UnitSalesReturnDetail> poDetail;
    private final String pxeDetTable = "Sales_Detail";
}