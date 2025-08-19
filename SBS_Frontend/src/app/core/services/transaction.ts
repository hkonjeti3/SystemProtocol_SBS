import { user } from './user';
import { account } from './account';

export class transaction {
  transactionId?: string;
  user?: user;
  senderAcc?: account;
  receiverAcc?: account;
  senderAccountNumber?: string;
  receiverAccountNumber?: string;
  transactionType: string | undefined | null;
  amount?: string;
  createdBy?: string;
  createdtime?: Date;
  lastModifiedBy?: string;
  lastModifiedtime?: Date;
  status?: string;

  constructor(user: user) {
    this.user = user;
  }
}
  