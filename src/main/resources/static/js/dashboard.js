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

  // The backend returns just the short code (e.g. "abc123"),
  // not a full URL. Handle both cases gracefully.
  function extractShortCode(shortUrl) {
    if (!shortUrl) return '';
    try {
      const url = new URL(shortUrl);
      return url.pathname.replace(/^\//, '');
    } catch {
      // It's already just a short code
      return shortUrl;
    }
  }

  // Build the full clickable short URL from a short code
  function buildFullUrl(shortCode) {
    return `${window.location.origin}/${shortCode}`;
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

  // ─── SVG Icons (inline, no emojis) ───
  const icons = {
    copy: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
    qr: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="8" height="8" rx="1"/><rect x="14" y="2" width="8" height="8" rx="1"/><rect x="2" y="14" width="8" height="8" rx="1"/><path d="M14 14h2v2h-2zm4 0h2v2h-2zm-4 4h2v2h-2zm4 0h2v2h-2z"/></svg>',
    analytics: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 20V10M12 20V4M6 20v-6"/></svg>',
    trash: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
    lock: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>',
    clicks: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
    calendar: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>',
    redirect: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 14l-4-4 4-4"/><path d="M5 10h11a4 4 0 0 1 0 8h-1"/></svg>',
    tag: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>'
  };

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
      const fullUrl = buildFullUrl(shortCode);
      const shortDisplay = fullUrl.replace(/^https?:\/\//, '');

      const card = document.createElement('div');
      card.className = 'url-card animate-slide-up';
      card.style.animationDelay = `${index * 0.05}s`;
      card.dataset.shortCode = shortCode;

      const isPasswordProtected = item.passwordProtected || !!item.passwordHash || !!item.password;

      card.innerHTML = `
        <div class="url-card-info">
          <div class="url-card-top">
            <a href="${escapeHtml(fullUrl)}" class="url-short" target="_blank" rel="noopener">
              ${escapeHtml(shortDisplay)}
            </a>
            <span class="badge ${item.active ? 'badge-active' : 'badge-inactive'}">
              ${item.active ? 'Active' : 'Inactive'}
            </span>
            ${isPasswordProtected ? `
              <span class="badge" style="background: var(--warning-bg); color: var(--warning); border: 1px solid rgba(251, 191, 36, 0.2);" title="Password Protected">
                ${icons.lock} Protected
              </span>` : ''}
            ${item.customAlias ? `
              <span class="badge" style="background: rgba(102,126,234,0.12); color: var(--accent); border: 1px solid rgba(102,126,234,0.2);" title="Custom Alias">
                ${icons.tag} ${escapeHtml(item.customAlias)}
              </span>` : ''}
          </div>
          <div class="url-long" title="${escapeHtml(item.longUrl || '')}">${escapeHtml(truncateUrl(item.longUrl))}</div>
          ${item.fallbackUrl ? `
            <div class="url-fallback" style="font-size: var(--text-xs); color: var(--text-secondary); margin-top: var(--space-1); display: flex; align-items: center; gap: 4px;" title="Fallback URL: ${escapeHtml(item.fallbackUrl)}">
              ${icons.redirect} Fallback: <a href="${escapeHtml(item.fallbackUrl)}" target="_blank" rel="noopener" style="color: var(--text-secondary); text-decoration: underline;">${escapeHtml(truncateUrl(item.fallbackUrl, 50))}</a>
            </div>` : ''}
          <div class="url-card-meta">
            <span class="url-meta-item">${icons.clicks} ${formatNumber(item.clickCount || 0)} clicks</span>
            <span class="url-meta-item">${icons.calendar} Expires ${formatDate(item.expiresAt)}</span>
          </div>
        </div>
        <div class="url-card-actions">
          <button class="btn-icon" title="Copy link" data-action="copy">${icons.copy}</button>
          <button class="btn-icon" title="View QR code" data-action="qr">${icons.qr}</button>
          <button class="btn-icon" title="Analytics" data-action="analytics">${icons.analytics}</button>
          <label class="toggle" title="${item.active ? 'Deactivate' : 'Activate'}">
            <input type="checkbox" ${item.active ? 'checked' : ''} data-action="toggle">
            <span class="toggle-slider"></span>
          </label>
          <button class="btn-icon" title="Delete" data-action="delete" style="color: var(--danger);">${icons.trash}</button>
        </div>
      `;

      // Event delegation
      card.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-action]');
        if (!btn) return;

        const action = btn.dataset.action;
        switch (action) {
          case 'copy':
            copyToClipboard(fullUrl);
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

  async function initNavbar() {
    try {
      const res = await fetch('/api/v1/auth/me');
      if (res.status === 401) {
        window.location.href = '/login.html';
        return false;
      }
      if (res.ok) {
        const user = await res.json();
        if (navLinks) {
          navLinks.innerHTML = `
            <li><a href="index.html">Home</a></li>
            <li><a href="dashboard.html" class="active">Dashboard</a></li>
            <li><a href="https://github.com" target="_blank" rel="noopener">GitHub</a></li>
            <li><button id="logoutBtn" class="btn btn-secondary" style="padding: 6px 12px; font-size: var(--text-sm); border: 1px solid rgba(0, 0, 0, 0.12); border-radius: var(--radius-sm); background: linear-gradient(to bottom, #ffffff, #f5f5f7); color: #1d1d1f; box-shadow: 0 1px 2px rgba(0,0,0,0.05); cursor: pointer; font-weight: 500;">Sign Out</button></li>
          `;
          const logoutBtn = document.getElementById('logoutBtn');
          if (logoutBtn) {
            logoutBtn.addEventListener('click', async () => {
              await fetch('/api/v1/auth/logout', { method: 'POST' });
              window.location.href = 'index.html';
            });
          }
        }
        return true;
      }
    } catch (err) {
      console.error('Navbar init error:', err);
    }
    return false;
  }

  // ─── Init ───
  async function init() {
    const authenticated = await initNavbar();
    if (authenticated) {
      await loadUrls();
    }
  }
  init();

})();
