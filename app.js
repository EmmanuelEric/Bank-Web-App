const createForm = document.getElementById("createForm");
const depositForm = document.getElementById("depositForm");
const withdrawForm = document.getElementById("withdrawForm");
const transferForm = document.getElementById("transferForm");
const balanceForm = document.getElementById("balanceForm");
const closeForm = document.getElementById("closeForm");
const accountList = document.getElementById("accountList");
const totalBalance = document.getElementById("totalBalance");
const accountCount = document.getElementById("accountCount");
const balanceResult = document.getElementById("balanceResult");
const messageBox = document.getElementById("message");
const API_BASE =
  window.location.protocol === "http:" || window.location.protocol === "https:"
    ? `${window.location.protocol}//${window.location.host}`
    : "http://localhost:8080";

function showMessage(text) {
  messageBox.textContent = text;
  messageBox.classList.add("show");
  clearTimeout(showMessage.timeout);
  showMessage.timeout = setTimeout(
    () => messageBox.classList.remove("show"),
    2200,
  );
}

async function loadAccounts() {
  try {
    const response = await fetch(`${API_BASE}/api/accounts`);
    const data = await response.json();
    totalBalance.textContent = formatCurrency(data.totalBalance || 0);
    accountCount.textContent = data.accountCount || 0;

    if (!data.accounts || data.accounts.length === 0) {
      accountList.innerHTML =
        '<div class="account-item">No accounts yet. Create the first one.</div>';
      return;
    }

    accountList.innerHTML = data.accounts
      .map(
        (account) => `
          <div class="account-item">
            <strong>${escapeHtml(account.ownerName)} • ${escapeHtml(account.id)}</strong>
            <div>Balance: ${formatCurrency(account.balance)}</div>
          </div>
        `,
      )
      .join("");
  } catch (error) {
    showMessage("Unable to load accounts.");
  }
}

async function createAccount(event) {
  event.preventDefault();
  const name = document.getElementById("ownerName").value.trim();
  if (!name) {
    showMessage("Please enter a name.");
    return;
  }

  const response = await fetch(`${API_BASE}/api/accounts`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `ownerName=${encodeURIComponent(name)}`,
  });

  const data = await response.json();
  if (response.ok) {
    showMessage(`Created account ${data.account.id}`);
    createForm.reset();
    loadAccounts();
  } else {
    showMessage(data.message || "Unable to create account.");
  }
}

async function depositFunds(event) {
  event.preventDefault();
  const accountId = document.getElementById("depositAccount").value.trim();
  const amount = document.getElementById("depositAmount").value;
  const response = await fetch(
    `${API_BASE}/api/accounts/${encodeURIComponent(accountId)}/deposit`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `amount=${encodeURIComponent(amount)}`,
    },
  );

  const data = await response.json();
  if (response.ok) {
    showMessage(`Deposited ${formatCurrency(amount)} into ${accountId}`);
    depositForm.reset();
    loadAccounts();
  } else {
    showMessage(data.message || "Deposit failed.");
  }
}

async function withdrawFunds(event) {
  event.preventDefault();
  const accountId = document.getElementById("withdrawAccount").value.trim();
  const amount = document.getElementById("withdrawAmount").value;
  const response = await fetch(
    `${API_BASE}/api/accounts/${encodeURIComponent(accountId)}/withdraw`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `amount=${encodeURIComponent(amount)}`,
    },
  );

  const data = await response.json();
  if (response.ok) {
    showMessage(`Withdrew ${formatCurrency(amount)} from ${accountId}`);
    withdrawForm.reset();
    loadAccounts();
  } else {
    showMessage(data.message || "Withdrawal failed.");
  }
}

async function transferFunds(event) {
  event.preventDefault();
  const fromAccount = document.getElementById("transferFrom").value.trim();
  const toAccount = document.getElementById("transferTo").value.trim();
  const amount = document.getElementById("transferAmount").value;

  const response = await fetch(
    `${API_BASE}/api/accounts/${encodeURIComponent(fromAccount)}/transfer`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `toAccount=${encodeURIComponent(toAccount)}&amount=${encodeURIComponent(amount)}`,
    },
  );

  const data = await response.json();
  if (response.ok) {
    showMessage(
      `Transferred ${formatCurrency(amount)} from ${fromAccount} to ${toAccount}`,
    );
    transferForm.reset();
    loadAccounts();
  } else {
    showMessage(data.message || "Transfer failed.");
  }
}

async function checkBalance(event) {
  event.preventDefault();
  const accountId = document.getElementById("balanceAccount").value.trim();
  const response = await fetch(
    `${API_BASE}/api/accounts/${encodeURIComponent(accountId)}`,
  );
  const data = await response.json();
  if (response.ok) {
    balanceResult.innerHTML = `<strong>${escapeHtml(data.account.ownerName)}</strong><div>Balance: ${formatCurrency(data.account.balance)}</div>`;
  } else {
    balanceResult.textContent = data.message || "Account not found.";
  }
}

async function closeAccount(event) {
  event.preventDefault();
  const accountId = document.getElementById("closeAccount").value.trim();
  const response = await fetch(
    `${API_BASE}/api/accounts/${encodeURIComponent(accountId)}`,
    {
      method: "DELETE",
    },
  );
  const data = await response.json();
  if (response.ok) {
    showMessage(`Closed account ${accountId}`);
    closeForm.reset();
    loadAccounts();
    if (balanceResult.textContent.includes(accountId)) {
      balanceResult.innerHTML = "Select an account to see its balance.";
    }
  } else {
    showMessage(data.message || "Unable to close account.");
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatCurrency(value) {
  return `$${Number(value).toFixed(2)}`;
}

createForm.addEventListener("submit", createAccount);
depositForm.addEventListener("submit", depositFunds);
withdrawForm.addEventListener("submit", withdrawFunds);
transferForm.addEventListener("submit", transferFunds);
balanceForm.addEventListener("submit", checkBalance);
closeForm.addEventListener("submit", closeAccount);

loadAccounts();
