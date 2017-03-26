/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author SAP Training
 */
import com.siebel.data.SiebelBusComp;
import com.siebel.data.SiebelBusObject;
import com.siebel.data.SiebelDataBean;
import com.siebel.data.SiebelException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.jdbc.OracleTypes;

public class ReceiptsByINVID {
    
    private String invNumber;
    private String invoiceId;
    private StringWriter errors = new StringWriter();
  
    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }
    
    
    public String getInvNumber() {
        return invNumber;
    }

    public void setInvNumber(String invNumber) {
        this.invNumber = invNumber;
    }
    
    public void getInvReceipts(){        
        CallableStatement callablestatement = null;
        String RecProc = "{call plexGetReceiptsByInvId(?,?,?,?,?,?)}";

        try {
            //log.debug("Calling Strored Procedure...");
            
            callablestatement = ApplicationsConnection.connectToEBSDatabase().prepareCall(RecProc);
            callablestatement.registerOutParameter(1, OracleTypes.CURSOR);
            callablestatement.setString(2, invNumber);
            callablestatement.registerOutParameter(3, java.sql.Types.NUMERIC);
            callablestatement.registerOutParameter(4, java.sql.Types.NUMERIC);
            callablestatement.registerOutParameter(5, java.sql.Types.DATE);
            callablestatement.registerOutParameter(6, java.sql.Types.NUMERIC);
            
            callablestatement.execute();
            ResultSet R= (ResultSet)callablestatement.getObject(1);                                           
            
        } catch (SQLException e) {
            //log.error("Error in calling Stored Procedure "+e);
            e.printStackTrace(new PrintWriter(errors));
            MyLogging.log(Level.INFO,"Error in getInvReceipts. Error in SQL: " + errors.toString());            
        }finally {
            if (callablestatement != null) {
                try{
                    callablestatement.close();
                }catch(SQLException ex){
                    ex.printStackTrace(new PrintWriter(errors));
                    MyLogging.log(Level.INFO,"Error in getInvReceipts. Error in closing SQL Connection: " + errors.toString());
                }
                
            }
        }

    }

    public void insertReceiptDataIntoSiebel(ResultSet R) {
        
        try{            
            SiebelDataBean sdb = ApplicationsConnection.connectSiebelServer();
            MyLogging.log(Level.INFO,"Printing Receipts.....");                     
           String paymentNuumber;
           SiebelBusObject fsInvBusObj = sdb.getBusObject("FS Invoice");
           SiebelBusComp fsInvBusComp = fsInvBusObj.getBusComp("FS Invoice");
           fsInvBusComp.activateField("Payment #");
           fsInvBusComp.setViewMode(3);
           fsInvBusComp.clearToQuery();
           fsInvBusComp.setSearchSpec("Invoice Id", this.invoiceId);
           boolean iRec = fsInvBusComp.firstRecord();           
           while(iRec){
               paymentNuumber = fsInvBusComp.getFieldValue("Payment #");
               
               iRec = fsInvBusComp.nextRecord();
           }
           
           sdb.logoff();
        }catch (IOException io){
            io.printStackTrace(new PrintWriter(errors));
            MyLogging.log(Level.INFO,"Error in getInvReceipts. Error in connection to Siebel: " + errors.toString());        
        }catch (SiebelException e) {
            //log.error("Error in calling Stored Procedure "+e);
            e.printStackTrace(new PrintWriter(errors));
            MyLogging.log(Level.SEVERE,"Error in getInvReceipts. Error in siebel connection: " + errors.toString());            
        }
    }
    
    private void processReceipts(String paymentNuumber, SiebelBusComp fsInvBusComp,ResultSet R) throws SQLException{
        boolean recExist = false;
        while (R.next()){
            if(R.getString(1).equalsIgnoreCase(paymentNuumber))
                recExist = true;                        
        }
        if(!recExist){
            try {
                fsInvBusComp.newRecord(0);
                
            } catch (SiebelException ex) {
                ex.printStackTrace(new PrintWriter(errors));
                MyLogging.log(Level.SEVERE,"Error in processReceipt. Error in siebel new record: " + errors.toString());            
            }
           
        }            
    }
    
    private void processReceipts(ResultSet R) throws SQLException, IOException{        
        MyLogging.log(Level.INFO,"In processReceipts");
        System.out.println("RECEIPT NO " + "|" + " AMOUNT PAID" + "|" + "      RECEIPT DATE" + "|" + " AMOUNT APPLIED" );
        System.out.println(R.getString(1) + "        |" + R.getString(2) + "       |" + R.getString(3) + "         |" + R.getString(4));
        SiebelDataBean sdb = ApplicationsConnection.connectSiebelServer();
        while (R.next()){
            Receipts receiptObj = new Receipts(R.getString(1),R.getString(2),R.getString(3),R.getString(4));           
            insertReceiptDataIntoSiebel(sdb, receiptObj);
        }        
    }
    
    public void insertReceiptDataIntoSiebel(SiebelDataBean sdb,Receipts receiptObj) {
        MyLogging.log(Level.INFO,"In insertReceiptDataIntoSiebel");
        try{                                                      
           String paymentNuumber;
           String reciptNum = receiptObj.getReciptNum();
           boolean recExist = false;
           SiebelBusObject fsInvBusObj = sdb.getBusObject("FS Invoice");
           SiebelBusComp fsInvBusComp = fsInvBusObj.getBusComp("FS Invoice");
           fsInvBusComp.activateField("Payment #");
           fsInvBusComp.setViewMode(3);
           fsInvBusComp.clearToQuery();
           fsInvBusComp.setSearchSpec("Invoice Id", this.invoiceId);
           boolean iRec = fsInvBusComp.firstRecord();           
           while(iRec){
               paymentNuumber = fsInvBusComp.getFieldValue("Payment #");
               if(reciptNum.equalsIgnoreCase(paymentNuumber))
                    recExist = true;
               iRec = fsInvBusComp.nextRecord();
           }
           MyLogging.log(Level.INFO,"Record Exists :"+recExist);  
           if(!recExist){                
                fsInvBusComp.newRecord(0);
                fsInvBusComp.setFieldValue("Payment #", reciptNum);
                fsInvBusComp.setFieldValue("Amount", receiptObj.getAmountPaid());
                fsInvBusComp.setFieldValue("Payment Date", receiptObj.getReciptDate());
                //fsInvBusComp.setFieldValue(reciptNum, receiptObj);
                fsInvBusComp.writeRecord();
           }         
           
        }catch (SiebelException e) {
            //log.error("Error in calling Stored Procedure "+e);
            e.printStackTrace(new PrintWriter(errors));
            MyLogging.log(Level.SEVERE,"Error in getInvReceipts. Error in siebel connection: " + errors.toString());            
        }
    }
    
    public static void main(String[] args) throws SQLException {

        ReceiptsByINVID test = new ReceiptsByINVID();
        test.invNumber ="10178";
        test.getInvReceipts();
    }
    
}
