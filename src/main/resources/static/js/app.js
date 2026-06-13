/* ============================================
   VectorLink — Landing Page Logic (app.js)
   ============================================ */

(function () {
  'use strict';

  // ─── DOM References ───
  const form          = document.getElementById('shortenForm');
  const longUrlInput  = document.getElementById('longUrlInput');
  const aliasInput    = document.getElementById('customAliasInput');
  const ttlSelect     = document.getElementById('ttlSelect');
  const submitBtn     = document.getElementById('submitBtn');
  const submitText    = document.getElementById('submitText');
  const submitSpinner = document.getElementById('submitSpinner');
  const advancedToggleBtn      = document.getElementById('advancedToggleBtn');
  const advancedOptionsSection = document.getElementById('advancedOptionsSection');
  const fallbackUrlInput       = document.getElementById('fallbackUrlInput');
  const passwordInput          = document.getElementById('passwordInput');
  const customExpiryGroup      = document.getElementById('customExpiryGroup');
  const customExpiryInput      = document.getElementById('customExpiryInput');

  // ─── Advanced Options Toggle ───
  if (advancedToggleBtn && advancedOptionsSection) {
    advancedToggleBtn.addEventListener('click', () => {
      advancedOptionsSection.classList.toggle('hidden');
    });
  }

  // ─── Custom Expiry Toggle ───
  if (ttlSelect && customExpiryGroup) {
    // Set default min datetime to now
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    if (customExpiryInput) {
      customExpiryInput.min = now.toISOString().slice(0, 16);
      // Default to 7 days from now
      const defaultDate = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
      defaultDate.setMinutes(defaultDate.getMinutes() - defaultDate.getTimezoneOffset());
      customExpiryInput.value = defaultDate.toISOString().slice(0, 16);
    }

    ttlSelect.addEventListener('change', () => {
      if (ttlSelect.value === 'custom') {
        customExpiryGroup.classList.remove('hidden');
      } else {
        customExpiryGroup.classList.add('hidden');
      }
    });
  }

  const resultCard    = document.getElementById('resultCard');
  const resultShortUrl= document.getElementById('resultShortUrl');
  const resultLongUrl = document.getElementById('resultLongUrl');
  const resultExpiry  = document.getElementById('resultExpiry');
  const resultAlias   = document.getElementById('resultAlias');
  const resultQr      = document.getElementById('resultQr');
  const copyBtn       = document.getElementById('copyBtn');

  const navbar        = document.getElementById('navbar');
  const navToggle     = document.getElementById('navToggle');
  const navLinks      = document.getElementById('navLinks');

  // ─── Navbar Scroll Effect ───
  window.addEventListener('scroll', () => {
    navbar.classList.toggle('scrolled', window.scrollY > 20);
  });

  // ─── Mobile Nav Toggle ───
  if (navToggle) {
    navToggle.addEventListener('click', () => {
      navLinks.classList.toggle('open');
    });
  }

  // ─── Toast System ───
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
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

  // ─── URL Validation ───
  function isValidUrl(string) {
    try {
      const url = new URL(string);
      return url.protocol === 'http:' || url.protocol === 'https:';
    } catch {
      return false;
    }
  }

  // ─── Loading State ───
  function setLoading(loading) {
    submitBtn.disabled = loading;
    submitText.textContent = loading ? '' : 'Shorten';
    submitSpinner.classList.toggle('hidden', !loading);
    if (loading) {
      submitText.textContent = 'Shortening…';
    }
  }

  // ─── Format Date ───
  function formatDate(iso) {
    if (!iso) return 'No expiry';
    const d = new Date(iso);
    return d.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  // ─── Calculate TTL seconds ───
  function getTtlSeconds() {
    if (ttlSelect.value === 'custom') {
      if (!customExpiryInput || !customExpiryInput.value) {
        return 604800; // Default 7 days
      }
      const expiryDate = new Date(customExpiryInput.value);
      const now = new Date();
      const diffSeconds = Math.floor((expiryDate - now) / 1000);
      if (diffSeconds <= 0) {
        return null; // Invalid — will be caught in validation
      }
      return diffSeconds;
    }
    return parseInt(ttlSelect.value, 10);
  }

  // ─── Form Submission ───
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const longUrl = longUrlInput.value.trim();
    if (!longUrl) {
      showToast('Please enter a URL.', 'warning');
      longUrlInput.focus();
      return;
    }

    if (!isValidUrl(longUrl)) {
      showToast('Please enter a valid URL starting with http:// or https://', 'error');
      longUrlInput.focus();
      return;
    }

    const customAlias = aliasInput.value.trim() || null;
    const ttlSeconds = getTtlSeconds();
    const fallbackUrl = fallbackUrlInput ? (fallbackUrlInput.value.trim() || null) : null;
    const password = passwordInput ? (passwordInput.value || null) : null;

    if (ttlSeconds === null) {
      showToast('Please select a future date for the expiry.', 'error');
      if (customExpiryInput) customExpiryInput.focus();
      return;
    }

    if (fallbackUrl && !isValidUrl(fallbackUrl)) {
      showToast('Please enter a valid fallback URL starting with http:// or https://', 'error');
      fallbackUrlInput.focus();
      return;
    }

    setLoading(true);
    resultCard.classList.add('hidden');

    try {
      const response = await fetch('/api/v1/urls', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          longUrl,
          ttlSeconds,
          customAlias,
          fallbackUrl,
          password
        })
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || err.error || `Server error (${response.status})`);
      }

      const data = await response.json();
      displayResult(data);
      showToast('Link created successfully!', 'success');

      // Clear form
      longUrlInput.value = '';
      aliasInput.value = '';
      if (fallbackUrlInput) fallbackUrlInput.value = '';
      if (passwordInput) passwordInput.value = '';
      if (advancedOptionsSection) advancedOptionsSection.classList.add('hidden');

    } catch (err) {
      console.error('Shorten error:', err);
      showToast(err.message || 'Something went wrong. Please try again.', 'error');
    } finally {
      setLoading(false);
    }
  });

  // ─── Display Result ───
  function displayResult(data) {
    // Backend returns shortUrl as just the short code, build full URL
    const shortCode = data.shortUrl || data.customAlias || '';
    const fullUrl = `${window.location.origin}/${shortCode}`;

    resultShortUrl.href = fullUrl;
    resultShortUrl.textContent = fullUrl.replace(/^https?:\/\//, '');

    resultLongUrl.textContent = data.longUrl;
    resultExpiry.innerHTML = `<strong>Expires:</strong> ${formatDate(data.expiresAt)}`;

    if (data.customAlias) {
      resultAlias.innerHTML = `<strong>Alias:</strong> ${escapeHtml(data.customAlias)}`;
      resultAlias.classList.remove('hidden');
    } else {
      resultAlias.classList.add('hidden');
    }

    // QR Code
    if (data.qrCodeUrl) {
      resultQr.src = data.qrCodeUrl;
    } else {
      resultQr.src = `/api/v1/qr/${shortCode}`;
    }

    resultCard.classList.remove('hidden');

    // Scroll to result
    resultCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // ─── Copy to Clipboard ───
  copyBtn.addEventListener('click', async () => {
    const url = resultShortUrl.href;
    try {
      await navigator.clipboard.writeText(url);
      copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px;"><polyline points="20 6 9 17 4 12"/></svg> Copied!';
      showToast('Link copied to clipboard!', 'success');
      setTimeout(() => {
        copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px;"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy';
      }, 2000);
    } catch {
      // Fallback
      const input = document.createElement('input');
      input.value = url;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px;"><polyline points="20 6 9 17 4 12"/></svg> Copied!';
      showToast('Link copied to clipboard!', 'success');
      setTimeout(() => {
        copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px;"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy';
      }, 2000);
    }
  });

  // ─── Animated Counters ───
  function animateCounters() {
    const counters = document.querySelectorAll('.stat-number[data-target]');
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const el = entry.target;
          const target = parseInt(el.dataset.target, 10);
          const prefix = el.dataset.prefix || '';
          const suffix = el.dataset.suffix || '';
          const duration = 2000;
          const start = performance.now();

          function update(now) {
            const elapsed = now - start;
            const progress = Math.min(elapsed / duration, 1);
            // Ease out cubic
            const eased = 1 - Math.pow(1 - progress, 3);
            const current = Math.round(eased * target);

            if (target >= 1000) {
              el.textContent = prefix + (current / 1000).toFixed(current >= 10000 ? 0 : 1) + 'K' + suffix;
            } else {
              el.textContent = prefix + current + suffix;
            }

            if (progress < 1) {
              requestAnimationFrame(update);
            }
          }

          requestAnimationFrame(update);
          observer.unobserve(el);
        }
      });
    }, { threshold: 0.3 });

    counters.forEach(c => observer.observe(c));
  }

  // ─── Feature Cards Intersection Observer ───
  function initScrollAnimations() {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.style.opacity = '1';
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.15 });

    document.querySelectorAll('.animate-slide-up').forEach(el => {
      observer.observe(el);
    });
  }

  // ─── Init ───
  animateCounters();
  initScrollAnimations();

})();
