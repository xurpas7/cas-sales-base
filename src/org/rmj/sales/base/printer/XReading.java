package org.rmj.sales.base.printer;

import java.io.File;
import org.json.simple.JSONObject;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.StringHelper;
import org.rmj.replication.utility.MiscReplUtil;

public class XReading {
    private final String DIR = System.getProperty("pos.clt.dir.ejournal");
    private final String DIR_BAK = System.getProperty("pos.clt.dir.ejournal.bak");
    
    StringBuilder builder;
    String sMessage;
    int nReportTyp;
    
    JSONObject oJSON;
    JSONObject oHeader;
    JSONObject oDetail;
    
    public XReading(JSONObject foJSON, int fnValue){
        oJSON = foJSON;
        nReportTyp = fnValue;
    }
    
    public boolean Print(){
        if (oJSON == null){
            setMessage("JSON object for printing is not set.");
            return false;
        }
        
        try {
            oHeader = (JSONObject) oJSON.get("Header");
            oDetail = (JSONObject) oJSON.get("Detail");
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
                
        builder = new StringBuilder();
        
        if (!printHeader()) return false;
        
        if (!printDetail()) return false;
        
        //save in text file
        
        File f = new File(DIR + (String) oHeader.get("sMINumber")+  " " + System.getProperty("pos.clt.date") + ".txt");
        
        if (nReportTyp != EndShift.YREADING){
            if(f.exists() && !f.isDirectory()) { 
                builder.append("\n").append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");

                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString(), true);
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString(), true);
                
                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " X-" + System.getProperty("pos.clt.date") +".txt", builder.toString(), true);
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " X-" + System.getProperty("pos.clt.date") +".txt", builder.toString(), true);
            } else {
                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString());
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString());
                
                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " X-" + System.getProperty("pos.clt.date") +".txt", builder.toString());
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " X-" + System.getProperty("pos.clt.date") +".txt", builder.toString());
            }
        }
        
        return true;
    }
    
    private boolean printDetail(){
        try {
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '*')).append("\n").append("\n");
            
            String lsClosed = (String) oDetail.get("dClosedxx");
            if (lsClosed == null) lsClosed = "";
            
            builder.append(StringHelper.postpad(" Shift Start : " + (String) oDetail.get("dOpenedxx"), CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.postpad(" Shift End   : " + lsClosed, CharSize.REGULAR)).append("\n").append("\n");
            
            builder.append(StringHelper.postpad(" Beginning SI: " + (String) oDetail.get("sORNoFrom"), CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.postpad(" Ending SI   : " + (String) oDetail.get("sORNoThru"), CharSize.REGULAR)).append("\n").append("\n");
            
            double lnSales = Double.parseDouble(String.valueOf(oDetail.get("nSalesAmt"))) - 
                                (Double.parseDouble(String.valueOf(oDetail.get("nDiscount"))) + 
                                    Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))) + 
                                    Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))));
            
            
            builder.append(StringHelper.postpad(" Beginning Balance : ", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.valueOf(String.valueOf(oDetail.get("nAccuSale"))) - lnSales, "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad(" Ending Balance    : ", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.valueOf(String.valueOf(oDetail.get("nAccuSale"))), "##0.00"), CharSize.REGLEN)).append("\n").append("\n");
            
            builder.append(StringHelper.postpad(" GROSS SALES", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nSalesAmt"))) + 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nVoidAmnt"))) + 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nReturnsx"))), "##0.00"), CharSize.REGLEN))
                    .append("\n");
            
            builder.append(StringHelper.postpad(" Less : Regular Discnt", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nDiscount"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            //builder.append(StringHelper.postpad("        Senior Dscnt/PWD", 25)).append(" ")
            //        .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))) + 
            //                                                                Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("        Returns", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nReturnsx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            //builder.append(StringHelper.postpad("        Void", 25)).append(" ")
            //        .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nVoidAmnt"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("", 25)).append(" ").append(StringHelper.prepad("-", CharSize.REGLEN, '-')).append("\n");
            
            builder.append(StringHelper.postpad(" NET SALES", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nSalesAmt"))) - 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nDiscount"))) + 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))) + 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))), "##0.00"), CharSize.REGLEN)).append("\n\n");
            
            builder.append(StringHelper.postpad(" VATable Sales", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nVATSales"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad(" VAT Amount", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nVATAmtxx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad(" VAT-Exempt Sales", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nNonVATxx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad(" Zero-Rated Sales", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nZeroRatd"))), "##0.00"), CharSize.REGLEN)).append("\n\n");
            
            /*
            builder.append(StringHelper.postpad(" Senior/PWD Gross Sales:", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nNonVATxx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("   Senior/PWD Net Sales:", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nNonVATxx"))) - 
                                                                            ((Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))) + 
                                                                                    Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("     Less: 20% Discount:", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("           Less 12% VAT:", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))), "##0.00"), CharSize.REGLEN)).append("\n\n");
            */
            
            builder.append(StringHelper.postpad(" Collection Info:", 25)).append("\n");
            
            builder.append(StringHelper.postpad("  Petty Cash", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nOpenBalx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Cash Pull-out", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nCPullOut"))), "##0.00"), CharSize.REGLEN)).append("\n\n");
            
            builder.append(StringHelper.postpad("  Cashbox Amount", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nOpenBalx"))) + 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nCashAmnt"))) - 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nCPullOut"))) - 
                                                                            Double.parseDouble(String.valueOf(oDetail.get("nReturnsx"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Cheque", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nChckAmnt"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Credit Card", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nCrdtAmnt"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Gift Cheque", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nGiftAmnt"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Company Accounts", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(oDetail.get("nChrgAmnt"))), "##0.00"), CharSize.REGLEN)).append("\n");
            
            /*
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            
            double lnSales = Double.parseDouble(String.valueOf(oDetail.get("nSalesAmt"))) - 
                                (Double.parseDouble(String.valueOf(oDetail.get("nDiscount"))) + 
                                    Double.parseDouble(String.valueOf(oDetail.get("nPWDDiscx"))) + 
                                    Double.parseDouble(String.valueOf(oDetail.get("nVATDiscx"))));
            
            
            builder.append(StringHelper.postpad("  Beginning Balance : ", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.valueOf(String.valueOf(oDetail.get("nAccuSale"))) - lnSales, "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.postpad("  Ending Balance    : ", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(Double.valueOf(String.valueOf(oDetail.get("nAccuSale"))), "##0.00"), CharSize.REGLEN)).append("\n");
            */

            builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            
            builder.append(StringHelper.postpad("  Total Sales for the day", 25)).append(" ")
                    .append(StringHelper.prepad(CommonUtils.NumberFormat(lnSales, "##0.00"), CharSize.REGLEN)).append("\n");
            
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '*')).append("\n");
            
            builder.append("/end-of-summary - ").append((String) oDetail.get("dSysDatex"));
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private boolean printHeader(){
        try {
            String sBranchNm = (String) oHeader.get("sBranchNm");
            String sCompnyNm = (String) oHeader.get("sCompnyNm");
            String sAddress1 = (String) oHeader.get("sAddress1");
            String sAddress2 = (String) oHeader.get("sAddress2");
            String sVATREGTN = (String) oHeader.get("sVATREGTN");
            String sMINumber = (String) oHeader.get("sMINumber");
            String sSerialNo = (String) oHeader.get("sSerialNo");
            String dTransact = (String) oHeader.get("dTransact");
            String sCashierx = (String) oHeader.get("sCashierx");
            String sTerminal = (String) oHeader.get("sTerminal");
            
            builder.append(StringHelper.midpad(sBranchNm, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sCompnyNm, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sAddress1, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sAddress2, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("VAT REG TIN: " + sVATREGTN, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("MIN: " + sMINumber, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("Serial No: " + sSerialNo, CharSize.REGULAR)).append("\n").append("\n");
            
            switch (nReportTyp) {
                case EndShift.XREADING:
                    builder.append(StringHelper.midpad("X-READING", CharSize.REGULAR)).append("\n");
                    break;
                case EndShift.YREADING:
                    builder.append(StringHelper.midpad("Y-READING", CharSize.REGULAR)).append("\n");
                    break;
                default:
                    builder.append(StringHelper.midpad("UNKNOWN REP", CharSize.REGULAR)).append("\n");
                    break;
            }

            builder.append("\n");
            
            builder.append(StringHelper.postpad("DATE    : " + dTransact, CharSize.REGULAR, ' ')).append("\n");
            builder.append(StringHelper.postpad("CASHIER : " + sCashierx, CharSize.REGULAR, ' ')).append("\n");
            builder.append(StringHelper.postpad("TERMINAL: " + sTerminal, CharSize.REGULAR, ' ')).append("\n").append("\n");
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
        
        return true;
    }
    
    
    public String getMessage(){
        return sMessage;
    }
    private void setMessage(String fsValue){
        sMessage = fsValue;
    }
}
