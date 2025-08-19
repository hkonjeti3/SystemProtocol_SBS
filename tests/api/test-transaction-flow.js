const axios = require('axios');

// Configuration
const BASE_URL = 'http://localhost:8081/api/v1';
const ADMIN_USERNAME = 'admin'; // Replace with actual admin username
const ADMIN_PASSWORD = 'admin'; // Replace with actual admin password
const CUSTOMER_USERNAME = 'customer'; // Replace with actual customer username
const CUSTOMER_PASSWORD = 'customer'; // Replace with actual customer password

// Test data
const testTransaction = {
  transactionType: 'CREDIT',
  amount: '100.00',
  senderAccountNumber: '1234567890', // Replace with actual account number
  receiverAccountNumber: '0987654321', // Replace with actual account number
  user: {
    userId: 1 // Replace with actual customer user ID
  }
};

async function testCompleteTransactionFlow() {
  console.log('🚀 Starting Complete Transaction Flow Test...\n');
  
  try {
    // Step 1: Customer Login
    console.log('📋 Step 1: Customer Login');
    const customerLoginResponse = await axios.post(`${BASE_URL}/login`, {
      username: CUSTOMER_USERNAME,
      password: CUSTOMER_PASSWORD
    });
    
    if (!customerLoginResponse.data || !customerLoginResponse.data.userId) {
      throw new Error('Customer login failed');
    }
    
    const customerUserId = customerLoginResponse.data.userId;
    console.log(`✅ Customer logged in successfully. User ID: ${customerUserId}\n`);
    
    // Step 2: Create Transaction Request
    console.log('📋 Step 2: Create Transaction Request');
    const transactionResponse = await axios.post(`${BASE_URL}/account/credit/request`, testTransaction);
    
    if (!transactionResponse.data) {
      throw new Error('Transaction request creation failed');
    }
    
    console.log('✅ Transaction request created successfully');
    console.log(`   Response: ${transactionResponse.data}\n`);
    
    // Step 3: Admin Login
    console.log('📋 Step 3: Admin Login');
    const adminLoginResponse = await axios.post(`${BASE_URL}/login`, {
      username: ADMIN_USERNAME,
      password: ADMIN_PASSWORD
    });
    
    if (!adminLoginResponse.data || !adminLoginResponse.data.userId) {
      throw new Error('Admin login failed');
    }
    
    const adminUserId = adminLoginResponse.data.userId;
    console.log(`✅ Admin logged in successfully. User ID: ${adminUserId}\n`);
    
    // Step 4: Get All Transactions (Admin View)
    console.log('📋 Step 4: Get All Transactions (Admin View)');
    const transactionsResponse = await axios.get(`${BASE_URL}/transactions/all`);
    
    if (!transactionsResponse.data || !transactionsResponse.data.transactions) {
      throw new Error('Failed to retrieve transactions');
    }
    
    const transactions = transactionsResponse.data.transactions;
    const pendingTransactions = transactions.filter(t => t.status === 'PENDING');
    
    console.log(`✅ Retrieved ${transactions.length} total transactions`);
    console.log(`   Pending transactions: ${pendingTransactions.length}`);
    
    if (pendingTransactions.length > 0) {
      console.log('   Sample pending transaction:', pendingTransactions[0]);
    }
    console.log('');
    
    // Step 5: Approve Transaction (if any pending)
    if (pendingTransactions.length > 0) {
      console.log('📋 Step 5: Approve Transaction');
      const transactionToApprove = pendingTransactions[0];
      const transactionId = transactionToApprove.transactionId || transactionToApprove.id;
      
      if (transactionId) {
        const approvalResponse = await axios.post(`${BASE_URL}/approval-workflow/transaction/approve/${transactionId}`);
        
        if (approvalResponse.data) {
          console.log('✅ Transaction approved successfully');
          console.log(`   Response: ${JSON.stringify(approvalResponse.data)}`);
        } else {
          console.log('❌ Transaction approval failed');
        }
      } else {
        console.log('⚠️  No transaction ID found for approval');
      }
    } else {
      console.log('⚠️  No pending transactions to approve');
    }
    
    console.log('\n🎉 Transaction Flow Test Completed Successfully!');
    
  } catch (error) {
    console.error('❌ Test failed with error:', error.message);
    if (error.response) {
      console.error('   Response status:', error.response.status);
      console.error('   Response data:', error.response.data);
    }
    process.exit(1);
  }
}

// Run the test
testCompleteTransactionFlow();
