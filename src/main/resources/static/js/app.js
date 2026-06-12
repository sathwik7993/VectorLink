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
      navToggle.textContent = navLinks.classList.contains('open') ? '✕' : '☰';
    });
  }

  // ─── Toast System ───
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const icons = {
      success: '✅',
      error: '❌',
      info: 'ℹ️',
      warning: '⚠️'
    };

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
      <span class="toast-icon">${icons[type] || icons.info}</span>
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
    const ttlSeconds = parseInt(ttlSelect.value, 10);

    setLoading(true);
    resultCard.classList.add('hidden');

    try {
      const response = await fetch('/api/v1/urls', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          longUrl,
          ttlSeconds,
          customAlias
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

    } catch (err) {
      console.error('Shorten error:', err);
      showToast(err.message || 'Something went wrong. Please try again.', 'error');
    } finally {
      setLoading(false);
    }
  });

  // ─── Display Result ───
  function displayResult(data) {
    const shortUrl = data.shortUrl || `${window.location.origin}/${data.customAlias || ''}`;

    resultShortUrl.href = shortUrl;
    resultShortUrl.textContent = shortUrl.replace(/^https?:\/\//, '');

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
      // Derive short code from the URL
      const shortCode = shortUrl.split('/').pop();
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
      copyBtn.textContent = '✓ Copied!';
      showToast('Link copied to clipboard!', 'success');
      setTimeout(() => {
        copyBtn.innerHTML = '📋 Copy';
      }, 2000);
    } catch {
      // Fallback
      const input = document.createElement('input');
      input.value = url;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      copyBtn.textContent = '✓ Copied!';
      showToast('Link copied to clipboard!', 'success');
      setTimeout(() => {
        copyBtn.innerHTML = '📋 Copy';
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
