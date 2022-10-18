import java.io.Serializable;
import java.util.List;

public class ReplicatedAccountInfo implements Serializable {
    private List<Transaction> executedList;

    private List<Transaction> outstandingList;

    private double balance;

    private int orderCounter;

    private int outStandingCounter;

    public ReplicatedAccountInfo(List<Transaction> executedList,
                                 List<Transaction> outstandingList,
                                 double balance,
                                 int orderCounter,
                                 int outStandingCounter) {
        this.executedList = executedList;
        this.outstandingList = outstandingList;
        this.balance = balance;
        this.orderCounter = orderCounter;
        this.outStandingCounter = outStandingCounter;
    }

    public int getOrderCounter() {
        return this.orderCounter;
    }

    public void setOrderCounter(int orderCounter) {
        this.orderCounter = orderCounter;
    }

    public List<Transaction> getExecutedList() {
        return this.executedList;
    }

    public void setExecutedList(List<Transaction> executedList) {
        this.executedList = executedList;
    }

    public List<Transaction> getOutstandingList() {
        return this.outstandingList;
    }

    public void setOutstandingList(List<Transaction> outstandingList) {
        this.outstandingList = outstandingList;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void addInExecutedList(Transaction transaction) {
        executedList.add(transaction);
    }

    /**
     * add in the outstanding list when the function is called
     * @param transaction
     */
    public synchronized void addInOutStandingList(Transaction transaction) {
        this.outstandingList.add(transaction);
        this.outStandingCounter++;
    }

    /**
     * add interest to the balance when the function is called and remove from the outstanding and add it to the executed
     * @param transaction
     */
    public synchronized void addInterest(Transaction transaction) {
        Transaction transInList = this.outstandingList.stream()
                .filter(it -> it.getUniqueId().equals(transaction.getUniqueId())).findFirst().orElse(null);
        this.balance = this.balance * (1.0 + transaction.getAmount() / 100.0);
        this.executedList.add(transaction);
        this.orderCounter++;
        this.outstandingList.remove(transInList);
    }

    /**
     * get synced balance when the function called
     * @param transaction
     */
    public synchronized void getSyncedBalance(Transaction transaction) {
        Transaction transInList = this.outstandingList.stream()
                .filter(it -> it.getUniqueId().equals(transaction.getUniqueId())).findFirst().orElse(null);
        System.out.println("Synced Balance : " + this.balance);
        this.outstandingList.remove(transInList);
    }

    /**
     * add deposit when the function is called
     * @param transaction
     */
    public synchronized void addDeposit(Transaction transaction) {
        Transaction transInList = this.outstandingList.stream()
                .filter(it -> it.getUniqueId().equals(transaction.getUniqueId())).findFirst().orElse(null);
        this.balance = this.balance + transaction.getAmount();
        this.executedList.add(transaction);
        this.orderCounter++;
        this.outstandingList.remove(transInList);
    }


    public int getOutStandingCounter() {
        return outStandingCounter;
    }

    public void setOutStandingCounter(int outStandingCounter) {
        this.outStandingCounter = outStandingCounter;
    }

    public synchronized void incrementOutstandingCounter() {
        this.outStandingCounter++;
    }
}
