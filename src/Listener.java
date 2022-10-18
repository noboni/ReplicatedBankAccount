import spread.AdvancedMessageListener;
import spread.SpreadException;
import spread.SpreadMessage;

public class Listener implements AdvancedMessageListener {

    public void regularMessageReceived(SpreadMessage message) {

        try {
                Transaction transaction = (Transaction) message.getObject();
                switch (transaction.getTransactionType()){
                    //call the functions according to the type
                    case DEPOSIT:
                        ReplicatedBankAccount.replicatedAccountInfo.addDeposit(transaction);
                        break;
                    case INTEREST:
                        ReplicatedBankAccount.replicatedAccountInfo.addInterest(transaction);
                        break;
                    case SYNCED_BALANCE:
                        ReplicatedBankAccount.replicatedAccountInfo.getSyncedBalance(transaction);
                        break;
                    case INITIALIZE_BALANCE:
                        ReplicatedBankAccount.replicatedAccountInfo.setBalance(transaction.getAmount());
                        break;
                }

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public synchronized void membershipMessageReceived(SpreadMessage spreadMessage) {
        ReplicatedBankAccount.membershipReplica = spreadMessage.getMembershipInfo().getMembers();
        notifyAll();
    }

}