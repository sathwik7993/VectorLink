/* ============================================
   VectorLink — Password Entry Logic (password.js)
   ============================================ */

(function () {
  'use strict';

  // ─── Extract query parameters ───
  const urlParams = new URLSearchParams(window.location.search);
  const code = urlParams.get('code');

  if (!code) {
    showToast('Missing short link code.', 'error');
    setTimeout(() => {
      window.location.href = '/';
    }, 2000);
    return;
  }

  // ─── DOM References ───
  const form          = document.getElementById('passwordForm');
  const passwordInput = document.getElementById('passwordInput');
  const submitBtn     = document.getElementById('submitBtn');
  const submitText    = document.getElementById('submitText');
  const submitSpinner = document.getElementById('submitSpinner');
  const errorBlock    = document.getElementById('errorBlock');
  const errorText     = document.getElementById('errorText');

  // ─── Toast System ───
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    if (type === 'error') {
      toast.style.background = 'rgba(248, 113, 113, 0.1)';
      toast.style.borderColor = 'rgba(248, 113, 113, 0.25)';
      toast.style.boxShadow = '0 0 20px rgba(248, 113, 113, 0.15)';
    }
    toast.innerHTML = `
      <span class="toast-icon"></span>
      <span class="toast-message">${escapeHtml(message)}</span>
    `;

    container.appendChild(toast);

    setTimeout(() => {
      toast.classList.add('removing');
      toast.addEventListener('animationend', () => toast.remove());
    }, 3500);
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // ─── Loading State ───
  function setLoading(loading) {
    submitBtn.disabled = loading;
    submitText.textContent = loading ? 'Unlocking…' : 'Unlock Link';
    submitSpinner.classList.toggle('hidden', !loading);
  }

  // ─── Form Submission ───
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const password = passwordInput.value;
    if (!password) {
      showToast('Please enter a password.', 'warning');
      passwordInput.focus();
      return;
    }

    setLoading(true);
    errorBlock.classList.add('hidden');

    try {
      const response = await fetch('/api/v1/urls/verify-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          shortCode: code,
          password: password
        })
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || err.error || `Server error (${response.status})`);
      }

      const data = await response.json();
      
      if (data.success) {
        // The server has set an HttpOnly access cookie for this link
        showToast('Password verified! Redirecting...', 'success');

        setTimeout(() => {
          window.location.href = `/${code}`;
        }, 800);
      } else {
        throw new Error('Incorrect password. Access denied.');
      }

    } catch (err) {
      console.error('Verify password error:', err);
      const msg = err.message || 'Verification failed. Please try again.';
      
      errorText.textContent = msg;
      errorBlock.classList.remove('hidden');
      
      showToast(msg, 'error');
      
      errorBlock.style.animation = 'none';
      errorBlock.offsetHeight;
      errorBlock.style.animation = '';
      
      passwordInput.select();
    } finally {
      setLoading(false);
    }
  });

})();
