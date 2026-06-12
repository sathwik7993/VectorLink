/* ============================================
   VectorLink — Dashboard Logic (dashboard.js)
   ============================================ */

(function () {
  'use strict';

  // ─── State ───
  let allUrls = [];

  // ─── DOM ───
  const urlList      = document.getElementById('urlList');
  const pageLoader   = document.getElementById('pageLoader');
  const emptyState   = document.getElementById('emptyState');
  const noResults    = document.getElementById('noResults');
  const searchInput  = document.getElementById('searchInput');
  const navbar       = document.getElementById('navbar');
  const navToggle    = document.getElementById('navToggle');
  const navLinks     = document.getElementById('navLinks');
  const modalContainer = document.getElementById('modalContainer');

  const statTotalLinks  = document.getElementById('statTotalLinks');
  const statTotalClicks = document.getElementById('statTotalClicks');
  const statActiveLinks = document.getElementById('statActiveLinks');

  // ─── Navbar ───
  window.addEventListener('scroll', () => {
    navbar.classList.toggle('scrolled', window.scrollY > 20);
  });

  if (navToggle) {
    navToggle.addEventListener('click', () => {
      navLinks.classList.toggle('open');
      navToggle.textContent = navLinks.classList.contains('open') ? '✕' : '☰';
    });
  }

  // ─── Toast System ───
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const icons = { success: '✅', error: '❌', info: 'ℹ️', warning: '⚠️' };
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

  // ─── Format Helpers ───
  function formatDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric'
    });
  }

  function formatNumber(n) {
    if (n === undefined || n === null) return '0';
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return n.toString();
  }

  function truncateUrl(url, maxLen = 55) {
    if (!url) return '';
    return url.length > maxLen ? url.substring(0, maxLen) + '…' : url;
  }

  function extractShortCode(shortUrl) {
    if (!shortUrl) return '';
    // Try to extract the path after the last /
    try {
      const url = new URL(shortUrl);
      return url.pathname.replace(/^\//, '');
    } catch {
      return shortUrl.split('/').pop() || shortUrl;
    }
  }

  // ─── Fetch All URLs ───
  async function loadUrls() {
    pageLoader.classList.remove('hidden');
    emptyState.classList.add('hidden');
    noResults.classList.add('hidden');
    urlList.innerHTML = '';

    try {
      const res = await fetch('/api/v1/urls');
      if (!res.ok) throw new Error(`Server error (${res.status})`);

      allUrls = await res.json();

      updateStats();
      renderUrls(allUrls);

    } catch (err) {
      console.error('Load error:', err);
      showToast('Failed to load URLs. Please try again.', 'error');
    } finally {
      pageLoader.classList.add('hidden');
    }
  }

  // ─── Update Summary Stats ───
  function updateStats() {
    statTotalLinks.textContent = allUrls.length;
    statTotalClicks.textContent = formatNumber(
      allUrls.reduce((sum, u) => sum + (u.clickCount || 0), 0)
    );
    statActiveLinks.textContent = allUrls.filter(u => u.active).length;
  }

  // ─── Render URL Cards ───
  function renderUrls(urls) {
    urlList.innerHTML = '';
    emptyState.classList.add('hidden');
    noResults.classList.add('hidden');

    if (allUrls.length === 0) {
      emptyState.classList.remove('hidden');
      return;
    }

    if (urls.length === 0) {
      noResults.classList.remove('hidden');
      return;
    }

    urls.forEach((item, index) => {
      const shortCode = extractShortCode(item.shortUrl);
      const shortDisplay = item.shortUrl
        ? item.shortUrl.replace(/^https?:\/\//, '')
        : shortCode;

      const card = document.createElement('div');
      card.className = 'url-card animate-slide-up';
      card.style.animationDelay = `${index * 0.05}s`;
      card.dataset.shortCode = shortCode;

      card.innerHTML = `
        <div class="url-card-info">
          <div class="url-card-top">
            <a href="${escapeHtml(item.shortUrl || '/' + shortCode)}" class="url-short" target="_blank" rel="noopener">
              ${escapeHtml(shortDisplay)}
            </a>
            <span class="badge ${item.active ? 'badge-active' : 'badge-inactive'}">
              ${item.active ? 'Active' : 'Inactive'}
            </span>
          </div>
          <div class="url-long" title="${escapeHtml(item.longUrl || '')}">${escapeHtml(truncateUrl(item.longUrl))}</div>
          <div class="url-card-meta">
            <span class="url-meta-item">👆 ${formatNumber(item.clickCount || 0)} clicks</span>
            <span class="url-meta-item">📅 Expires ${formatDate(item.expiresAt)}</span>
          </div>
        </div>
        <div class="url-card-actions">
          <button class="btn-icon" title="Copy link" data-action="copy">📋</button>
          <button class="btn-icon" title="View QR code" data-action="qr">📱</button>
          <button class="btn-icon" title="Analytics" data-action="analytics">📊</button>
          <label class="toggle" title="${item.active ? 'Deactivate' : 'Activate'}">
            <input type="checkbox" ${item.active ? 'checked' : ''} data-action="toggle">
            <span class="toggle-slider"></span>
          </label>
          <button class="btn-icon" title="Delete" data-action="delete" style="color: var(--danger);">🗑️</button>
        </div>
      `;

      // Event delegation
      card.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-action]');
        if (!btn) return;

        const action = btn.dataset.action;
        switch (action) {
          case 'copy':
            copyToClipboard(item.shortUrl || `${window.location.origin}/${shortCode}`);
            break;
          case 'qr':
            openQr(shortCode);
            break;
          case 'analytics':
            window.location.href = `analytics.html?code=${encodeURIComponent(shortCode)}`;
            break;
          case 'toggle':
            toggleUrl(shortCode, btn.checked);
            break;
          case 'delete':
            confirmDelete(shortCode);
            break;
        }
      });

      urlList.appendChild(card);
    });
  }

  // ─── Copy ───
  async function copyToClipboard(url) {
    try {
      await navigator.clipboard.writeText(url);
      showToast('Link copied to clipboard!', 'success');
    } catch {
      const input = document.createElement('input');
      input.value = url;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      showToast('Link copied to clipboard!', 'success');
    }
  }

  // ─── QR Code Modal ───
  function openQr(shortCode) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal" style="text-align: center;">
        <h3>QR Code</h3>
        <p>Scan this code to visit the link.</p>
        <div style="background: #fff; border-radius: var(--radius-lg); padding: 16px; display: inline-block; margin-bottom: var(--space-6);">
          <img src="/api/v1/qr/${encodeURIComponent(shortCode)}" alt="QR Code" style="width: 200px; height: 200px;">
        </div>
        <div class="modal-actions" style="justify-content: center;">
          <button class="btn btn-secondary" data-dismiss>Close</button>
        </div>
      </div>
    `;

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay || e.target.closest('[data-dismiss]')) {
        overlay.remove();
      }
    });

    modalContainer.appendChild(overlay);
  }

  // ─── Toggle Active / Inactive ───
  async function toggleUrl(shortCode, newState) {
    try {
      const res = await fetch(`/api/v1/urls/${encodeURIComponent(shortCode)}/toggle`, {
        method: 'PATCH'
      });
      if (!res.ok) throw new Error(`Server error (${res.status})`);

      // Update local state
      const url = allUrls.find(u => extractShortCode(u.shortUrl) === shortCode);
      if (url) url.active = !url.active;

      updateStats();
      showToast(`Link ${url && url.active ? 'activated' : 'deactivated'}.`, 'success');

      // Re-render to update badge
      const query = searchInput.value.trim().toLowerCase();
      renderUrls(filterUrls(query));

    } catch (err) {
      console.error('Toggle error:', err);
      showToast('Failed to toggle link status.', 'error');
      // Reload to reset state
      loadUrls();
    }
  }

  // ─── Delete Confirmation ───
  function confirmDelete(shortCode) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal">
        <h3>Delete Link?</h3>
        <p>This will permanently remove <strong>${escapeHtml(shortCode)}</strong> and all its analytics data. This action cannot be undone.</p>
        <div class="modal-actions">
          <button class="btn btn-secondary" data-dismiss>Cancel</button>
          <button class="btn btn-danger" data-confirm>Delete</button>
        </div>
      </div>
    `;

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay || e.target.closest('[data-dismiss]')) {
        overlay.remove();
      }
      if (e.target.closest('[data-confirm]')) {
        overlay.remove();
        deleteUrl(shortCode);
      }
    });

    modalContainer.appendChild(overlay);
  }

  async function deleteUrl(shortCode) {
    try {
      const res = await fetch(`/api/v1/urls/${encodeURIComponent(shortCode)}`, {
        method: 'DELETE'
      });
      if (!res.ok) throw new Error(`Server error (${res.status})`);

      allUrls = allUrls.filter(u => extractShortCode(u.shortUrl) !== shortCode);
      updateStats();

      const query = searchInput.value.trim().toLowerCase();
      renderUrls(filterUrls(query));

      showToast('Link deleted successfully.', 'success');

    } catch (err) {
      console.error('Delete error:', err);
      showToast('Failed to delete link.', 'error');
    }
  }

  // ─── Search / Filter ───
  function filterUrls(query) {
    if (!query) return allUrls;
    return allUrls.filter(u => {
      const shortCode = extractShortCode(u.shortUrl).toLowerCase();
      const longUrl = (u.longUrl || '').toLowerCase();
      return shortCode.includes(query) || longUrl.includes(query);
    });
  }

  searchInput.addEventListener('input', () => {
    const query = searchInput.value.trim().toLowerCase();
    renderUrls(filterUrls(query));
  });

  // ─── Init ───
  loadUrls();

})();
