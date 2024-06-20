package org.rmj.sales.base.printer;

import java.io.File;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.StringHelper;
import org.rmj.replication.utility.MiscReplUtil;

public class Invoice {
    private final String DIR = System.getProperty("pos.clt.dir.ejournal");
    private final String DIR_BAK = System.getProperty("pos.clt.dir.ejournal.bak");
    
    StringBuilder builder;
    String sMessage;
    
    JSONObject oJSON;
    JSONObject oHeader;
    JSONObject oMaster;
    JSONArray oDetail;
    JSONObject oPaymnt;
    JSONObject oFooter;
    
    public Invoice(JSONObject foJSON){
        oJSON = foJSON;
    }    
    
    public boolean Print(){
        if (oJSON == null){
            setMessage("JSON object for printing is not set.");
            return false;
        }
        
        try {
            oHeader = (JSONObject) oJSON.get("Header");
            oMaster = (JSONObject) oJSON.get("Master");
            oDetail = (JSONArray) oJSON.get("Detail");
            oPaymnt = (JSONObject) oJSON.get("Payment");
            oFooter = (JSONObject) oJSON.get("Footer");
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
                
        builder = new StringBuilder();
        
        if (!printHeader()) return false;
        
        if (!printDetail()) return false;
        
        if (!printFooter()) return false;
        
        //save in text file
        
        File f = new File(DIR + (String) oHeader.get("sMINumber")+  " " + System.getProperty("pos.clt.date") + ".txt");
        
        if(f.exists() && !f.isDirectory()) { 
            builder.append("\n").append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            
            if (!((String) oHeader.get("cReprintx")).equals("1")){
                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") + ".txt", builder.toString(), true);
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString(), true);
            }
        } else {
            if (!((String) oHeader.get("cReprintx")).equals("1")){
                MiscReplUtil.fileWrite(DIR + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString());
                MiscReplUtil.fileWrite(DIR_BAK + (String) oHeader.get("sMINumber") +  " " + System.getProperty("pos.clt.date") +".txt", builder.toString());
            }
        }
        
        return true;
    }
    
    private boolean printDetail(){
        try {
            double nTranTotl = Double.parseDouble(String.valueOf(oMaster.get("nTranTotl"))); //(double) oMaster.get("nTranTotl");
            double nDiscRate = Double.parseDouble(String.valueOf(oMaster.get("nDiscount"))); //(double) oMaster.get("nDiscount");
            double nAddDiscx = Double.parseDouble(String.valueOf(oMaster.get("nAddDiscx"))); //(double) oMaster.get("nAddDiscx");
            double nVATRatex = Double.parseDouble(String.valueOf(oMaster.get("nVATRatex"))); //(double) oMaster.get("nVATRatex");
            double nFreightx = Double.parseDouble(String.valueOf(oMaster.get("nFreightx"))); //(double) oMaster.get("nFreightx");
            
            //add freight charge to transaction total
            nTranTotl += nFreightx;
            
            if ("a".equalsIgnoreCase((String) oHeader.get("cTranMode"))){
                if ("or".equalsIgnoreCase((String) oHeader.get("sSlipType")))
                    builder.append(StringHelper.midpad("OFFICIAL RECEIPT", CharSize.REGULAR)).append(DIR);
                else 
                    builder.append(StringHelper.midpad("SALES INVOICE", CharSize.REGULAR)).append("\n");
            } else 
                builder.append(StringHelper.midpad("TRAINING MODE", CharSize.REGULAR)).append("\n");
            
            if ("1".equals((String) oHeader.get("cReprintx"))){
                builder.append("\n").append(StringHelper.midpad("REPRINT", CharSize.REGULAR)).append("\n");
            }
            
            builder.append("\n");
            builder.append(" Cashier: ").append(StringHelper.postpad((String) oMaster.get("sCashierx"), 30, ' ')).append("\n");
            builder.append(" Terminal No.: ").append(StringHelper.postpad((String) oMaster.get("sTerminal"), 25, ' ')).append("\n");
            
            if ("or".equalsIgnoreCase((String) oHeader.get("sSlipType")))
                builder.append(" OR No.: ").append(StringHelper.postpad((String) oMaster.get("sInvoicex"), 31, ' ')).append("\n");
            else 
                builder.append(" SI No.: ").append(StringHelper.postpad((String) oMaster.get("sInvoicex"), 31, ' ')).append("\n");
            
            builder.append(" Transaction No.: ").append(StringHelper.postpad((String) oMaster.get("sTransNox"), 22, ' ')).append("\n");
            builder.append(" Date/Time: ").append(StringHelper.postpad((String) oMaster.get("sDateTime"), 31, ' ')).append("\n");
            
            builder.append("\n\n");
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '*')).append("\n");
            
            //print detail header
            builder.append("QTY").append(" ")
                    .append(StringHelper.postpad("DESCRIPTION", CharSize.DSCLEN, ' '))
                    .append(StringHelper.prepad("UPRICE", CharSize.PRCLEN, ' '))
                    .append(StringHelper.prepad("AMOUNT", CharSize.TTLLEN, ' '))
                    .append("\n");
            
            double total;
            double nonvat = 0.00;
            int itemctr = 0;
            int nonvatctr = 0;
            
            //print detail
            for(Object item: oDetail){
                JSONObject loItem = (JSONObject) item;
                
                total = (int) loItem.get("nQuantity") * Double.parseDouble((String.valueOf(loItem.get("nAmountxx"))));
                
                builder.append(StringHelper.prepad(String.valueOf(loItem.get("nQuantity")), CharSize.QTYLEN, ' ')).append(" ")
                        .append(StringHelper.postpad(String.valueOf(loItem.get("sBarCodex")), CharSize.DSCLEN, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(Double.parseDouble((String.valueOf(loItem.get("nAmountxx")))), "##0.00")), CharSize.PRCLEN, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(total, "##0.00")), CharSize.TTLLEN, ' '));
                
                if ("1".equals((String) loItem.get("cVatablex")))
                    builder.append("V");
                else{
                    nonvat += total;
                    nonvatctr += (int) loItem.get("nQuantity");
                }
                    
                builder.append("\n");
                
                //indented description
                builder.append("    ").append(String.valueOf(loItem.get("sDescript")).toUpperCase()).append("\n");
                
                //if item was serialized
                if("1".equals((String) loItem.get("cSerialze"))){
                    if (!"".equals((String) loItem.get("xDescript")))
                        builder.append(StringHelper.postpad("     " + (String) loItem.get("xDescript"), CharSize.REGULAR)).append("\n");
                    
                    if (!"".equals((String) loItem.get("sSerial01")))
                        builder.append(StringHelper.postpad("     " + "SN: "+ (String) loItem.get("sSerial01"), CharSize.REGULAR)).append("\n");
                    
                    if (!"".equals((String) loItem.get("sSerial02")))
                        builder.append(StringHelper.postpad("     " + "SN: "+ (String) loItem.get("sSerial02"), CharSize.REGULAR)).append("\n");
                }
                
                double detdisc = total * Double.parseDouble(String.valueOf(loItem.get("nDiscount"))) + Double.parseDouble(String.valueOf(loItem.get("nAddDiscx")));
                
                if (detdisc > 0.00){
                    if (!((String) loItem.get("sPromoDsc")).isEmpty()){
                        builder.append(StringHelper.postpad("      " + ((String) loItem.get("sPromoDsc")).toUpperCase(), CharSize.REGULAR)).append("\n");
                    }
                    
                    if (Double.parseDouble(String.valueOf(loItem.get("nDiscount"))) > 0.00){
                        builder.append(StringHelper.postpad("       (" + (Double.parseDouble(String.valueOf(loItem.get("nDiscount"))) * 100) + "%)", 25, ' '))
                            .append(StringHelper.prepad(" (" + String.valueOf(CommonUtils.NumberFormat(total * Double.parseDouble(String.valueOf(loItem.get("nDiscount"))), "##0.00") + ")"), CharSize.REGLEN, ' ')).append("\n");
                    }

                    if (Double.parseDouble(String.valueOf(loItem.get("nAddDiscx"))) > 0.00){
                        builder.append(StringHelper.postpad("       (P" + String.valueOf(loItem.get("nAddDiscx")) + ")", 25, ' '))
                            .append(StringHelper.prepad(" (" +String.valueOf(CommonUtils.NumberFormat(Double.parseDouble(String.valueOf(loItem.get("nAddDiscx"))), "##0.00") + ")"), CharSize.REGLEN, ' ')).append("\n");
                    }
                }
                
                itemctr += (int) loItem.get("nQuantity");
            }
            
            if (nFreightx > 0.00){
                builder.append("\n");
                builder.append(StringHelper.prepad(" ", CharSize.QTYLEN, ' '))
                        .append(StringHelper.postpad("FREIGHT CHARGE", CharSize.DSCLEN, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nFreightx, "##0.00")), CharSize.PRCLEN, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nFreightx, "##0.00")), CharSize.TTLLEN, ' ')).append("\n");
            }
            
      
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            
            builder.append(" No. of Items: ").append(itemctr).append("\n").append("\n");
            
            builder.append(StringHelper.postpad(" Gross Sales", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nTranTotl, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            double discount = 0.00;
            
            if (nDiscRate + nAddDiscx > 0.00) discount = nTranTotl * nDiscRate + nAddDiscx;
            
            if (discount > 0.00){                
                builder.append(StringHelper.postpad(" Less: Discount", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(discount, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                
                if (!((String) oMaster.get("sPromoDsc")).isEmpty()){
                    builder.append(StringHelper.postpad(" Less: " + ((String) oMaster.get("sPromoDsc")).toUpperCase(), CharSize.REGULAR)).append("\n");
                }

                if (nDiscRate > 0.00){
                    builder.append(StringHelper.postpad("        (" + (nDiscRate * 100) + "%)", 26, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nTranTotl * nDiscRate, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                }

                if (nAddDiscx > 0.00){
                    builder.append(StringHelper.postpad("        (P" + String.valueOf(nAddDiscx) + ")", 26, ' '))
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nAddDiscx, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                }
            }           
            
            //small separator
            builder.append(StringHelper.postpad(" ", 25, ' ')).append(" ")
                    .append(StringHelper.prepad("-", CharSize.REGLEN, '-')).append("\n");
            
            builder.append(StringHelper.postpad(" VATable Sales", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nTranTotl - discount, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            //small separator
            builder.append(StringHelper.postpad(" ", 25, ' ')).append(" ")
                    .append(StringHelper.prepad("-", CharSize.REGLEN, '-')).append("\n");;
            
            double netWOVAT = (nTranTotl - discount) / nVATRatex;
            builder.append(StringHelper.postpad(" Net VATable Sales", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(netWOVAT, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            double addVATxx = netWOVAT * (nVATRatex - 1);
            builder.append(StringHelper.postpad(" Add: VAT", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(addVATxx, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            //small separator
            builder.append(StringHelper.postpad(" ", 25, ' ')).append(" ")
                    .append(StringHelper.prepad("-", CharSize.REGLEN, '-')).append("\n");;
            
            double nettotal = netWOVAT + addVATxx;
            builder.append(StringHelper.postpad(" TOTAL AMOUNT DUE :", 25, ' ')).append(" ")
                    .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nettotal, "##0.00")), CharSize.REGLEN, ' ')).append("\n").append("\n");
            
            //payment computation
            double cash = Double.parseDouble((String.valueOf(oPaymnt.get("nCashAmtx"))));
            
            JSONArray loCard = (JSONArray) oPaymnt.get("sCredtCrd");
            double card = 0.00;
            for(Object item: loCard){
                JSONObject loItem = (JSONObject) item;
                card += Double.parseDouble((String.valueOf(loItem.get("nAmountxx"))));
            }
            
            JSONArray loCheck = (JSONArray) oPaymnt.get("sCheckPay");
            double check = 0.00;
            for(Object item: loCheck){
                JSONObject loItem = (JSONObject) item;
                check += Double.parseDouble((String.valueOf(loItem.get("nAmountxx"))));
            }
            
            JSONArray loGC = (JSONArray) oPaymnt.get("sGiftCert");
            double gc = 0.00;
            for(Object item: loGC){
                JSONObject loItem = (JSONObject) item;
                gc += Double.parseDouble((String.valueOf(loItem.get("nAmountxx"))));
            }
            
            JSONArray loFinancer = (JSONArray) oPaymnt.get("sFinancer");
            double financer = 0.00;
            for(Object item: loFinancer){
                JSONObject loItem = (JSONObject) item;
                financer += Double.parseDouble((String.valueOf(loItem.get("nAmountxx"))));
            }          
            
            if (cash > 0.00)
                builder.append(StringHelper.postpad(" Cash", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(cash, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            if (card > 0.00)
                builder.append(StringHelper.postpad(" Credit Card", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(card, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            if (check > 0.00)
                builder.append(StringHelper.postpad(" Check", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(check, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            if (gc > 0.00)
                builder.append(StringHelper.postpad(" Gift Certificate", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(gc, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            if (financer > 0.00)
                builder.append(StringHelper.postpad(" Other", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(financer, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append("\n");
                    
            //change computation
            double change = 0.00;
            if (gc > nettotal)
                change = cash + card + check + financer;
            else
                change = (cash + card + check + gc + financer) - nettotal;
            
            builder.append(StringHelper.postpad(" CHANGE           :", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(change, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n").append("\n");
            
            builder.append(StringHelper.postpad(" VAT Exempt Sales      ", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(nonvat, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append(StringHelper.postpad(" Zero-Rated Sales      ", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(0.00, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append(StringHelper.postpad(" VATable Sales         ", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(netWOVAT, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append(StringHelper.postpad(" VAT Amount            ", 25, ' ')).append(" ")
                        .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(addVATxx, "##0.00")), CharSize.REGLEN, ' ')).append("\n");
            
            builder.append("\n");
            
            builder.append(" Cust Name: ").append(StringHelper.postpad((String) oMaster.get("sClientNm"), 28, ' ')).append("\n");
            builder.append(" Address: ").append(StringHelper.postpad((String) oMaster.get("sAddressx"), 30, ' ')).append("\n");
            builder.append(" TIN: ").append(StringHelper.postpad((String) oMaster.get("sTINumber"), 34, ' ')).append("\n");
            builder.append(" Bus. Style: ").append(StringHelper.postpad((String) oMaster.get("sBusStyle"), 27, ' ')).append("\n");            
            //display other payment breakdown
            if (card + check + gc + financer > 0.00){
                builder.append(StringHelper.midpad("", CharSize.REGULAR, '-')).append("\n");
            
                if (card > 0.00){
                    builder.append("\n");
                    
                    builder.append(" Credit Card(s): ").append("\n");
                    
                    for(Object item : loCard){
                        JSONObject loItem = (JSONObject) item;
                        builder.append(StringHelper.postpad(" " + (String) loItem.get("sBankCode"), 25, ' ')).append(" ")
                                .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(Double.parseDouble((String.valueOf(loItem.get("nAmountxx")))), "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                        
                        builder.append(" Card No. : ")
                                .append(StringHelper.prepad(String.valueOf(loItem.get("sCardNoxx")).substring(String.valueOf(loItem.get("sCardNoxx")).length() -4), CharSize.CARDLN, '*'))
                                .append("\n");
                    }
                }
                
                if (check > 0.00){
                    builder.append("\n");
                    
                    builder.append(" Check(s): ").append("\n");
                    
                    for(Object item : loCheck){
                        JSONObject loItem = (JSONObject) item;
                        builder.append(StringHelper.postpad(" " + (String) loItem.get("sBankCode"), 25, ' ')).append(" ")
                                .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(Double.parseDouble((String.valueOf(loItem.get("nAmountxx")))), "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                        
                        builder.append(" Check No. : ").append((String) loItem.get("sCheckNox")).append("\n");
                    }
                }
                
                if (gc > 0.00){
                    builder.append("\n");
                    
                    builder.append(" Gift Certificate(s): ").append("\n");
                    
                    for(Object item : loGC){
                        JSONObject loItem = (JSONObject) item;
                        builder.append(StringHelper.postpad(" " + (String) loItem.get("sCompnyCd"), 25, ' ')).append(" ")
                                .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(Double.parseDouble((String.valueOf(loItem.get("nAmountxx")))), "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                        
                        builder.append(" Reference No. : ").append((String) loItem.get("sReferNox")).append("\n");
                    }
                }
                
                if (financer > 0.00){
                    builder.append("\n");
                    
                    builder.append(" Other(s): ").append("\n");
                    
                    for(Object item : loFinancer){
                        JSONObject loItem = (JSONObject) item;
                        builder.append(StringHelper.postpad(" " + (String) loItem.get("sCompnyCd"), 25, ' ')).append(" ")
                                .append(StringHelper.prepad(String.valueOf(CommonUtils.NumberFormat(Double.parseDouble((String.valueOf(loItem.get("nAmountxx")))), "##0.00")), CharSize.REGLEN, ' ')).append("\n");
                        
                        builder.append(" Reference No. : ").append((String) loItem.get("sReferNox")).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private boolean printHeader(){
        try {
            String sCompnyNm = (String) oHeader.get("sCompnyNm");
            String sBranchNm = (String) oHeader.get("sBranchNm");
            String sAddress1 = (String) oHeader.get("sAddress1");
            String sAddress2 = (String) oHeader.get("sAddress2");
            String sVATREGTN = (String) oHeader.get("sVATREGTN");
            String sMINumber = (String) oHeader.get("sMINumber");
            String sSerialNo = (String) oHeader.get("sSerialNo");
            String sSlipType = (String) oHeader.get("sSlipType");
            String cReprintx = (String) oHeader.get("cReprintx");

            builder.append(StringHelper.midpad(sBranchNm, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sCompnyNm, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sAddress1, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad(sAddress2, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("VAT REG TIN: " + sVATREGTN, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("MIN: " + sMINumber, CharSize.REGULAR)).append("\n");
            builder.append(StringHelper.midpad("Serial No: " + sSerialNo, CharSize.REGULAR)).append("\n").append("\n\n");
        } catch (Exception e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private boolean printFooter(){
        try {
            String sSlipType = (String) oHeader.get("sSlipType");
            String sDevelopr = (String) oFooter.get("sDevelopr");
            String sAddress1 = (String) oFooter.get("sAddress1");
            String sAddress2 = (String) oFooter.get("sAddress2");
            String sVATREGTN = (String) oFooter.get("sVATREGTN");
            String sAccrNmbr = (String) oFooter.get("sAccrNmbr");
            String sAccrIssd = (String) oFooter.get("sAccrIssd");
            String sAccdExpr = (String) oFooter.get("sAccdExpr");
            String sPTUNmber = (String) oFooter.get("sPTUNmber");
//            String sPTUIssdx = (String) oFooter.get("sPTUIssdx");
//            String sPTUExpry = (String) oFooter.get("sPTUExpry");
            
            builder.append(StringHelper.midpad("", CharSize.REGULAR, '*')).append("\n").append("\n");
            
            if (!"1".equalsIgnoreCase((String) oHeader.get("cReprintx"))){
                if ("a".equalsIgnoreCase((String) oHeader.get("cTranMode"))){
                    if ("or".equalsIgnoreCase(sSlipType))
                        builder.append(StringHelper.midpad("This serves as an OFFICIAL RECEIPT.", CharSize.REGULAR));
                    else
                        builder.append(StringHelper.midpad("This serves as your SALES INVOICE.", CharSize.REGULAR));
                } else
                    builder.append(StringHelper.midpad("This is not a SALES INVOICE.", CharSize.REGULAR));
                
                builder.append("\n");
            }
            
            builder.append(StringHelper.midpad("Thank you, and please come again.", CharSize.REGULAR));
            
            builder.append("\n").append("\n");
            
            builder.append(StringHelper.midpad(sDevelopr, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad(sAddress1, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad(sAddress2, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("VAT REG TIN: " + sVATREGTN, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("ACCR NO.: " + sAccrNmbr, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("Date Issued: " + sAccrIssd, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("Valid Until: " + sAccdExpr, CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("PTU NO.: " + sPTUNmber, CharSize.REGULAR)).append("\n");
            
//            builder.append(StringHelper.midpad("Date Issued: " + sPTUIssdx, CharSize.REGULAR)).append("\n");
            
//            builder.append(StringHelper.midpad("Valid Until: " + sPTUExpry, CharSize.REGULAR)).append("\n").append("\n");
//            
//            if ("or".equalsIgnoreCase(sSlipType))
//                builder.append(StringHelper.midpad("THIS RECEIPT SHALL BE VALID", CharSize.REGULAR));
//            else
//                builder.append(StringHelper.midpad("THIS INVOICE SHALL BE VALID", CharSize.REGULAR));
//            
//            builder.append("\n");
//            builder.append(StringHelper.midpad("FOR FIVE(5) YEARS FROM THE DATE OF", CharSize.REGULAR));
//            builder.append("\n");
//            builder.append(StringHelper.midpad("THE PERMT TO USE.", CharSize.REGULAR)).append("\n");
            
            builder.append(StringHelper.midpad("----- END OF RECEIPT -----", CharSize.REGULAR)).append("\n");
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
