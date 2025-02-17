/**
 * Copyright (C) Sotirios Liaskos (liaskos@yorku.ca) - All Rights Reserved
 * 
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holder.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holder.  If you encounter this file and do not have
 * permission, please contact the copyright holder and delete this file.
 */
package ca.yorku.cmg.lob.exchange;

// IMPORTANT NOTE: FOR RUNNING THE WHOLE PROGRAM USING MAVEN, I NEEDED TO MAKE SOME CHANGES TO THE CODE, YET AFTER TRYING MULTIPLE TIMES
// I REALIZED THAT THERE IS AN IMPORTANT CLASS WHICH IS MISSING. BASED ON MY DEBUGS AND TROUBLESHOOTING, FOR THE PART HALFBooks, the class OrderBook is missing since asks = book.getAsks()
// and book is an instance of orderBook. so the class OrderBook.java is missing here for the code to run properly!



import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import ca.yorku.cmg.lob.orderbook.Ask;
import ca.yorku.cmg.lob.orderbook.Bid;
import ca.yorku.cmg.lob.orderbook.HalfBook;
import ca.yorku.cmg.lob.orderbook.OrderOutcome;
import ca.yorku.cmg.lob.orderbook.Orderbook;
import ca.yorku.cmg.lob.orderbook.Trade;
import ca.yorku.cmg.lob.security.Security;
import ca.yorku.cmg.lob.security.SecurityList;
import ca.yorku.cmg.lob.trader.Trader;
import ca.yorku.cmg.lob.trader.TraderInstitutional;
import ca.yorku.cmg.lob.trader.TraderRetail;
import ca.yorku.cmg.lob.tradestandards.IOrder;
import ca.yorku.cmg.lob.tradestandards.ITrade;


/**
 * Represents a stock exchange that manages securities, accounts, orders, and trades.
 */
public class Exchange {

	Orderbook book;
	HalfBook<Ask> asks;
	HalfBook<Bid> bids;
	PositionBook positionBook = new PositionBook();
	SecurityList securities = new SecurityList();
	AccountsList accounts = new AccountsList();
	ArrayList<Trade> tradesLog = new ArrayList<Trade>();
	
	long totalFees = 0;
	
    /**
     * Default constructor for the Exchange class.
     */
	public Exchange(){
	book = new Orderbook();
    asks = book.getAsks();
    bids = book.getBids();
	}
	
	
    /**
     * Validates an order to ensure it complies with exchange rules. Checks if trader and security are supported by the exchange, and that the trader has enough balance of the exchange. 
     * 
     * @param o the {@linkplain ca.yorku.cmg.lob.tradestandards.IOrder}-implementing object to be validated
     * @return {@code true} if the order is valid, {@code false} otherwise
     */
	public boolean validateOrder(IOrder o) {
	    if (securities.getSecurityByTicker(o.getSecurity().getTicker()) == null) {
	        System.err.println("Order rejected: Invalid ticker " + o.getSecurity().getTicker());
	        return false;
	    }
	    
	    if (accounts.getTraderAccount(o.getTrader()) == null) {
	        System.err.println("Order rejected: Trader not registered " + o.getTrader().getID());
	        return false;
	    }

	    int pos = positionBook.getPosition(o.getSecurity().getTicker());
	    long bal = accounts.getTraderAccount(o.getTrader()).getBalance();

	    if ((o instanceof Ask) && (pos < o.getQuantity())) {
	        System.err.println("Order rejected: Seller does not have enough shares " + o);
	        return false;
	    }
	    
	    if ((o instanceof Bid) && (bal < o.getValue())) {
	        System.err.println("Order rejected: Buyer does not have enough balance " + o);
	        return false;
	    }

	    System.out.println("Order accepted: " + o);
	    return true;
	}

	
    /**
     * Submits an order to the exchange for processing.
     * 
     * @param o     the {@linkplain ca.yorku.cmg.lob.tradestandards.IOrder}-implementing object to be processed
     * @param time the timestamp of the order submission (seconds)
     */
	public void submitOrder(IOrder o, long time) {
		System.out.println("Submitting Order: " + o);
		if (!validateOrder(o)){
			System.out.println("Order Rejected: " + o);
			return;
			
		}
		System.out.println("Order Validated: " + o);
		OrderOutcome oOutcome;

		// Check if order is a Bid
		if (o instanceof Bid) {
		    oOutcome = asks.processOrder((Bid) o, time);
		    if (oOutcome.getUnfulfilledOrder().getQuantity() > 0) {
		        bids.addOrder((Bid) oOutcome.getUnfulfilledOrder());
		    }
		} 
		// Check if order is an Ask
		else if (o instanceof Ask) {
		    oOutcome = bids.processOrder((Ask) o, time);
		    if (oOutcome.getUnfulfilledOrder().getQuantity() > 0) {
		        asks.addOrder((Ask) oOutcome.getUnfulfilledOrder());
		    }
		} 
		// If order is neither Bid nor Ask
		else {
		    System.err.println("Error: Order type is neither Bid nor Ask");
		    return;
		}
		//Save resulting trades to the tradesLog
		if (oOutcome.getResultingTrades() != null) {
			tradesLog.addAll(oOutcome.getResultingTrades());
		} else {
			 System.out.println("No trades executed for order: " + o);
			return;
		}
		
		//Calculate Fees for the trades
		for (ITrade t:oOutcome.getResultingTrades()) {
			
			//Update balances for Buyer
			
			//Get the fee that they buyer is supposed to pay
			int buyerFee = t.getBuyerFee();
			//Apply the above fee to the account balance of the buyer 			
			accounts.getTraderAccount(t.getBuyer()).withdrawMoney((int) buyerFee);
			//Apply the trade payment to the account balance of the buyer (they spent money)
			accounts.getTraderAccount(t.getBuyer()).withdrawMoney(t.getValue());
			//Add the bought stocks to the position of the buyer
			positionBook.addToPosition(t.getSecurity().getTicker(), t.getQuantity());
			
			//Update balances for Seller
			
			//Get the fee that the seller is supposed to pay
			long sellerFee = t.getSellerFee();
			//Apply the above fee to the account balance of the seller
			accounts.getTraderAccount(t.getSeller()).withdrawMoney((int) sellerFee);
			//Apply the trade payment to the account balance of the seller (they earned money)
			accounts.getTraderAccount(t.getSeller()).addMoney(t.getValue());
			//Deduct the sold stocks from the position of the seller
			positionBook.deductFromPosition(t.getSecurity().getTicker(), t.getQuantity());
			
			this.totalFees += t.getBuyerFee() + t.getSellerFee(); 
		}
	}
	
	
	
	//
	// I O 
	//
	
	
	
	
	
	
    /**
     * Reads the security list from a file and populates the exchange.
     * 
     * @param path the path to the security list file
     */
	public void readSecurityListfromFile(String path) {
	    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isFirstLine = true; // Skip header

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                String[] parts = line.split(",", -1); // Split by comma
                if (parts.length >= 2) {
                    String code = parts[0].trim();
                    String description = parts[1].trim();
                    securities.addSecurity(code, description);
                } else {
                    System.err.println("Skipping malformed line: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
    /**
     * Reads the accounts list from a file and populates the exchange.
     * 
     * @param path the path to the accounts list file
     */
	public void readAccountsListFromFile(String path) {
	    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isFirstLine = true; // Skip header

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                String[] parts = line.split(",", -1); // Split by comma
                if (parts.length >= 4) {
                    String traderTitle = parts[0].trim();
                    String traderType = parts[1].trim();
                    String accType = parts[2].trim();
                    long initBalance = Long.parseLong(parts[3].trim());
                	Trader t;
                    if (traderType.equals("Retail")) {
                    	t = new TraderRetail(traderTitle);
                    } else {
                    	t = new TraderInstitutional(traderTitle);
                    }
                    if (accType.equals("Basic")) {
                    	accounts.addAccount(new AccountBasic(t,initBalance));
                    } else {
                    	accounts.addAccount(new AccountPro(t,initBalance));
                    }
                } else {
                    System.err.println("Skipping malformed line (two few attributes): " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
    /**
     * Reads initial positions from a file and updates account holdings.
     * 
     * @param path the path to the initial positions file
     */
	public void readInitialPositionsFromFile(String path) {
	    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isFirstLine = true; // Skip header

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                String[] parts = line.split(",", -1); // Split by comma
                if (parts.length >= 3) {
                    Integer tid = Integer.valueOf(parts[0].trim());
                    String tkr = parts[1].trim();
                    Integer count = Integer.valueOf(parts[2].trim());
                    Trader trad = accounts.getTraderByID(tid); 
                    //does the trader id have an account? Is the ticker supported?
                    if (trad == null) {
                    	System.err.println("Initial Balances: Trader does not exist: " + line);
                    } else if (securities.getSecurityByTicker(tkr) == null) { 
                    	System.err.println("Initial Balances: Ticker not traded in this exchange: " + line);
                    } else {
                    	accounts.getTraderAccount(trad).updatePosition(tkr, count);
                    }
                } else {
                    System.err.println("Skipping malformed line (too few attributes): " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
		
    /**
     * Processes a file containing orders and submits them to the exchange.
     * 
     * @param path the path to the orders file
     */
	public void processOrderFile(String path) {
	    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isFirstLine = true; // Skip header

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                String[] parts = line.split(",", -1); // Split by comma
                if (parts.length >= 6) {
                    int traderID = Integer.valueOf(parts[0].trim());
                    String tkr = parts[1].trim();
                    String type = parts[2].trim();
                    int qty = Integer.valueOf(parts[3].trim());
                    int price = Integer.valueOf(parts[4].trim());
                    long time = Long.valueOf(parts[5].trim());
                    
                    Trader t = getAccounts().getTraderByID(traderID);
                    Security sec = getSecurities().getSecurityByTicker(tkr); 
                    
                    if ((t!=null) && (sec!=null)) {
                        if (type.equals("ask")) {
                        	submitOrder(new Ask(t,sec,price,qty,time), time);
                        } else if (type.equals("bid")) {
                        	submitOrder(new Bid(t,sec,price,qty,time), time);
                        } else {
                        	System.err.println("Order type not found (skipping): " + line);
                        }
                    }
                } else {
                    System.err.println("Skipping malformed line: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
    /**
     * Prints a table of current ask orders.
     * 
     * @param header whether to include a header in the output
     * @return a string representation of the ask table
     */
	public String printAskTable(boolean header) {
		return(book.getAsks().printAllOrders(header));
	}
	
    /**
     * Prints a table of current bid orders.
     * 
     * @param header whether to include a header in the output
     * @return a string representation of the bid table
     */
	public String printBidTable(boolean header) {
		return(book.getBids().printAllOrders(header));
	}
	
    /**
     * Prints a log of completed trades.
     * 
     * @param header whether to include a header in the output
     * @return a string representation of the trades log
     */
	public String printTradesLog(boolean header) {
		String output = "";
		if (header) {
			output = "[From____  To______  Tkr_  Quantity  Price__  Time____]\n";
			//"[%8d  %8d  %s  %8d  %7.2f  %8d]\n", 
		}
		for (Trade t: tradesLog) {
			output += t.toString();
		}
		return (output);
	}

    /**
     * Prints account balances of the exchange's customers
     * 
     * @param header whether to include a header in the output
     * @return a string representation of account balances
     */
	public String printBalances(boolean header) {
		return(accounts.debugPrintBalances(header));
	}
	
    /**
     * Prints the total fees collected by the exchange.
     * 
     * @param header whether to include a header in the output
     * @return a string representation of fees collected
     */
	public String printFeesCollected(boolean header) {
		if (header) {
			return (String.format("            Fees Collected TOTAL: %16s", 
					String.format("$%,.2f",this.totalFees/100.0)));
		} else {
			return (String.format("%16s", 
					String.format("$%,.2f",this.totalFees/100.0)));
		}
	}
	
	
	
	
	//
	// G E T T E R S
	//
		
	
    /**
     * Retrieves the list of securities managed by the exchange.
     * 
     * @return the {@linkplain ca.yorku.cmg.lob.security.SecurityList} object
     */
	public SecurityList getSecurities() {
		return securities;
	}
	
    /**
     * Retrieves the list of accounts managed by the exchange.
     * 
     * @return the {@linkplain ca.yorku.cmg.lob.exchange.AccountsList} object
     */
	public AccountsList getAccounts() {
		return accounts;
	}
}
