import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ReplicatedBankAccount {
    private static int noOfClients = 0;

    static SpreadGroup[] membershipReplica;

    static ReplicatedAccountInfo replicatedAccountInfo = new ReplicatedAccountInfo(
            new ArrayList<>(), new ArrayList<>(),
            0.0, 0, 0);


    public synchronized static void main(String[] args) throws InterruptedException {
        SpreadConnection connection = new SpreadConnection();
        Listener listener = new Listener();
        Random rand = new Random();
        int id = rand.nextInt();
        String clientName;
        System.out.println(id + "\n\n\n\n");
        try {
            connection.add(listener);
            connection.connect(InetAddress.getByName("127.0.0.1"), 8016, String.valueOf(id), false, true);

            SpreadGroup group = new SpreadGroup();
            group.join(connection, "group");
            System.out.println("Spread group name: " + connection.getPrivateGroup().toString());
            clientName = connection.getPrivateGroup().toString();

            synchronized (listener) {
                if (membershipReplica.length < 2) {
                    System.out.println("Waiting");
                    listener.wait();
                }

            }
            ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
            exec.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    List<Transaction> outstandingList =
                            replicatedAccountInfo.getOutstandingList();
                    if (outstandingList.size() > 0) {
                        System.out.println("Client name  :" + clientName +
                                " Size of outstanding" + outstandingList.size());
                        for (Transaction transaction : outstandingList) {
                            SpreadMessage message = new SpreadMessage();
                            message.addGroup(group);
                            message.setFifo();
                            try {
                                message.setObject(transaction);
                                connection.multicast(message);
                            } catch (SpreadException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }, 0, 60, TimeUnit.SECONDS);
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String input = scanner.nextLine();
                System.out.println(input);
                System.out.println("Done");
                System.out.println("\n\nNo of clients :" + noOfClients);
                if (input.equalsIgnoreCase("exit")) {
                    System.exit(0);
                } else if (input.equalsIgnoreCase("memberInfo")) {
                    for (int i = 0; i < membershipReplica.length; i++) {
                        System.out.println(membershipReplica[i].toString());
                    }

                } else if (input.equalsIgnoreCase("sleep ([0-9]*)")) {
                    String[] arrOfStr = input.split(" ");
                    long value;
                    try {
                        value = Long.parseLong(arrOfStr[1]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid input");
                    }
                    Thread.sleep(value);
                } else if (input.matches("deposit ([0-9]*)\\.([0-9]*)")) {
                    String[] arrOfStr = input.split(" ");
                    double value;
                    try {
                        value = Double.parseDouble(arrOfStr[1]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid input");
                    }
                    Transaction transaction = new Transaction();
                    transaction.setCommand(input);
                    transaction.setUniqueId(clientName + " " +
                            replicatedAccountInfo.getOutStandingCounter());
                    transaction.setAmount(value);
                    transaction.setTransactionType(TransactionType.DEPOSIT);
                    replicatedAccountInfo.addInOutStandingList(transaction);
                } else if (input.matches("addInterest ([0-9]*)\\.([0-9]*)")) {
                    String[] arrOfStr = input.split(" ");
                    double value;
                    try {
                        value = Double.parseDouble(arrOfStr[1]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid input");
                    }
                    Transaction transaction = new Transaction();
                    transaction.setCommand(input);
                    transaction.setUniqueId(clientName + " " +
                            replicatedAccountInfo.getOutStandingCounter());
                    transaction.setAmount(value);
                    transaction.setTransactionType(TransactionType.INTEREST);
                    replicatedAccountInfo.addInOutStandingList(transaction);
                }else if (input.equalsIgnoreCase("getQuickBalance")) {
                    System.out.println(replicatedAccountInfo.getBalance());
                }else if (input.equalsIgnoreCase("getSyncedBalance")) {
                    Transaction transaction = new Transaction();
                    transaction.setCommand(input);
                    transaction.setUniqueId(clientName + " " +
                            replicatedAccountInfo.getOutStandingCounter());
                    transaction.setTransactionType(TransactionType.DEPOSIT);
                    replicatedAccountInfo.addInOutStandingList(transaction);
                }else if(input.equalsIgnoreCase("getHistory")){
                    System.out.println("Outstanding collection:");
                    for (Transaction transaction : replicatedAccountInfo.getOutstandingList()){
                        if(!transaction.getTransactionType().equals(TransactionType.SYNCED_BALANCE)){
                            System.out.println(transaction.getCommand());
                        }
                    }
                    System.out.println("\n\nExecuted List:");
                    for (Transaction transaction : replicatedAccountInfo.getExecutedList()){
                        if(!transaction.getTransactionType().equals(TransactionType.SYNCED_BALANCE)){
                            System.out.println(transaction.getCommand());
                        }
                    }

                }else if(input.equalsIgnoreCase("checkTxStatus (.*)")) {
                    String[] arrOfStr = input.split(" ");
                    Transaction transactionInExecuted = replicatedAccountInfo.getExecutedList().stream()
                            .filter(it->it.getUniqueId().equals(arrOfStr[1])).findFirst().orElse(null);
                    if (transactionInExecuted != null){
                        System.out.println(transactionInExecuted.getCommand() + "is executed.");
                        continue;
                    }
                    Transaction transactionInOutstanding = replicatedAccountInfo.getOutstandingList().stream()
                            .filter(it->it.getUniqueId().equals(arrOfStr[1])).findFirst().orElse(null);
                    if (transactionInOutstanding != null){
                        System.out.println(transactionInOutstanding.getCommand() + "is not executed yet.");
                    }else {
                        System.out.println("Transaction not found");
                    }
                }else if (input.equalsIgnoreCase("cleanHistory")){
                    replicatedAccountInfo.setExecutedList(new ArrayList<>());
                    replicatedAccountInfo.setOrderCounter(0);
                }

            }
            //Thread.sleep(100000);*/


        } catch (SpreadException e) {
            e.printStackTrace();
            System.out.println(System.err);
            throw new RuntimeException(e);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (Exception e) {
            e.printStackTrace();
        }


        Thread.sleep(100000000);
    }

    public static void setNoOfClients(int numOfClients) {
        noOfClients = numOfClients;
    }


}