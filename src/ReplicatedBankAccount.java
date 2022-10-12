import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ReplicatedBankAccount {
    static SpreadGroup[] membershipReplica;

    static ReplicatedAccountInfo replicatedAccountInfo = new ReplicatedAccountInfo(
            new ArrayList<Transaction>(), new ArrayList<Transaction>(),
            0.0, 0, 0);


    public synchronized static void main(String[] args) throws InterruptedException {
        //Get all the argument info
        String serverAddress = args[0].trim();
        String accountName = args[1].trim();
        int numberOfReplica = Integer.parseInt(args[2].trim());
        String fileName = null;
        if (args.length == 4) {
            fileName = args[3];
        }
        SpreadConnection connection = new SpreadConnection();
        Listener listener = new Listener();
        Random rand = new Random();
        int id = rand.nextInt();
        String clientName;
        try {
            connection.add(listener);
            connection.connect(InetAddress.getByName(serverAddress), 8016, String.valueOf(id), false, true);

            //Create a group and join it with  the account
            SpreadGroup group = new SpreadGroup();
            group.join(connection, accountName);
            clientName = connection.getPrivateGroup().toString();
            System.out.println("Spread group name: " + clientName);
            System.out.println(membershipReplica.length);
            //wait for the listener to check if the  number of replicas joined as expected
            synchronized (listener) {
                if (membershipReplica.length < numberOfReplica) {
                    listener.wait();
                }

            }

            // create a scheduler thread and run every 10 seconds to check if any thing is left in the outstanding list
            // and send it to the listener.
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
            }, 0, 10, TimeUnit.SECONDS);

            //if there is a file take input from the file
            if (fileName != null) {
                BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
                String input = br.readLine();
                while (input != null) {
                    inputManipulation(clientName, input);
                    input = br.readLine();
                }

            }
            //else take input from the console
            else {
                while (true) {
                    Scanner scanner = new Scanner(System.in);
                    String input = scanner.nextLine();
                    inputManipulation(clientName, input);

                }
            }

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
    }

    /**
     * match the input patterns and call the functions accordingly
     *
     * @param clientName
     * @param input
     * @throws InterruptedException
     */
    private static void inputManipulation(String clientName, String input) throws InterruptedException {
        if (input.equalsIgnoreCase("exit")) {
            System.out.println("\n\nExiting");
            System.exit(0);
        } else if (input.equalsIgnoreCase("memberInfo")) {
            System.out.println("\n\nMembershipInfo:");
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
            System.out.println("\n\nSleeping for " + value + " seconds");
            Thread.sleep(value * 1000);
        } else if (input.matches("deposit ([0-9]*)(\\.)?([0-9]*)")) {
            String[] arrOfStr = input.split(" ");
            double value;
            try {
                value = Double.parseDouble(arrOfStr[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid input");
            }
            //generate transaction and add to the outstanding list
            System.out.println("\n\n" + input);
            Transaction transaction = new Transaction();
            transaction.setCommand(input);
            transaction.setUniqueId(clientName + " " +
                    replicatedAccountInfo.getOutStandingCounter());
            transaction.setAmount(value);
            transaction.setTransactionType(TransactionType.DEPOSIT);
            replicatedAccountInfo.addInOutStandingList(transaction);
        } else if (input.matches("addInterest ([0-9]*)(\\.)?([0-9]*)")) {
            System.out.println("\n\n" + input);
            String[] arrOfStr = input.split(" ");
            double value;
            try {
                value = Double.parseDouble(arrOfStr[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid input");
            }
            //generate transaction and add to the outstanding list
            Transaction transaction = new Transaction();
            transaction.setCommand(input);
            transaction.setUniqueId(clientName + " " +
                    replicatedAccountInfo.getOutStandingCounter());
            transaction.setAmount(value);
            transaction.setTransactionType(TransactionType.INTEREST);
            replicatedAccountInfo.addInOutStandingList(transaction);
        } else if (input.equalsIgnoreCase("getQuickBalance")) {
            System.out.println("\n\nExecuting getQuickBalance");
            System.out.println("Quick Balance" + replicatedAccountInfo.getBalance());
        } else if (input.equalsIgnoreCase("getSyncedBalance")) {
            System.out.println("\n\nExecuting getSyncedBalance");
            //generate transaction and add to the outstanding list
            Transaction transaction = new Transaction();
            transaction.setCommand(input);
            transaction.setUniqueId(clientName + " " +
                    replicatedAccountInfo.getOutStandingCounter());
            transaction.setTransactionType(TransactionType.SYNCED_BALANCE);
            replicatedAccountInfo.addInOutStandingList(transaction);
        } else if (input.equalsIgnoreCase("getHistory")) {
            System.out.println("Outstanding collection:");
            for (Transaction transaction : replicatedAccountInfo.getOutstandingList()) {
                if (!transaction.getTransactionType().equals(TransactionType.SYNCED_BALANCE)) {
                    System.out.println(transaction.getUniqueId() + ":" + transaction.getCommand());
                }
            }
            System.out.println("\n\nExecuted List:");
            for (Transaction transaction : replicatedAccountInfo.getExecutedList()) {
                if (!transaction.getTransactionType().equals(TransactionType.SYNCED_BALANCE)) {
                    System.out.println(transaction.getUniqueId() + ":" + transaction.getCommand());
                }
            }

        } else if (input.matches("checkTxStatus (.*)")) {
            String[] arrOfStr = input.split(" ");
            String transactionId = arrOfStr[1];
            //generate transaction id if it is from the file
            if (arrOfStr[1].contains("<")) {
                transactionId = clientName + " " + (replicatedAccountInfo.getOutStandingCounter() - 1);
            }
            System.out.println("\n\nTransaction status of " + transactionId);
            String finalTransactionId = transactionId;
            Transaction transactionInExecuted = replicatedAccountInfo.getExecutedList().stream()
                    .filter(it -> it.getUniqueId().equals(finalTransactionId)).findFirst().orElse(null);
            if (transactionInExecuted != null) {
                System.out.println(transactionInExecuted.getCommand() + "is executed.");
                return;
            }
            Transaction transactionInOutstanding = replicatedAccountInfo.getOutstandingList().stream()
                    .filter(it -> it.getUniqueId().equals(arrOfStr[1])).findFirst().orElse(null);
            if (transactionInOutstanding != null) {
                System.out.println(transactionInOutstanding.getCommand() + "is not executed yet.");
            } else {
                System.out.println("Transaction not found");
            }
        } else if (input.equalsIgnoreCase("cleanHistory")) {
            System.out.println("Executing clean history");
            replicatedAccountInfo.setExecutedList(new ArrayList<>());
            replicatedAccountInfo.setOrderCounter(0);
        }
    }

}