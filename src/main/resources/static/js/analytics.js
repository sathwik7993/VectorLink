/* ============================================
   VectorLink — Analytics Logic (analytics.js)
   ============================================ */

(function () {
  'use strict';

  // ─── Read short code from query params ───
  const params    = new URLSearchParams(window.location.search);
  const shortCode = params.get('code');

  // ─── DOM ───
  const titleEl       = document.getElementById('analyticsTitle');
  const longUrlEl     = document.getElementById('analyticsLongUrl');
  const badgeEl       = document.getElementById('analyticsBadge');
  const pageLoader    = document.getElementById('pageLoader');
  const emptyState    = document.getElementById('emptyState');
  const statsRow      = document.getElementById('statsRow');
  const chartGridMain = document.getElementById('chartGridMain');
  const chartGridSecondary = document.getElementById('chartGridSecondary');

  const statTotal     = document.getElementById('statTotalClicks');
  const statDesktop   = document.getElementById('statDesktop');
  const statMobile    = document.getElementById('statMobile');
  const statTablet    = document.getElementById('statTablet');

  const navbar        = document.getElementById('navbar');
  const navToggle     = document.getElementById('navToggle');
  const navLinks      = document.getElementById('navLinks');

  // Charts
  let clicksChart = null;
  let deviceChart = null;
  let browserChart = null;
  let osChart = null;

  // ─── Navbar ───
  window.addEventListener('scroll', () => {
    navbar.classList.toggle('scrolled', window.scrollY > 20);
  });

  if (navToggle) {
    navToggle.addEventListener('click', () => {
      navLinks.classList.toggle('open');
    });
  }

  // ─── Toast ───
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
      <span class="toast-icon"></span>
      <span class="toast-message">${message}</span>
    `;
    container.appendChild(toast);
    setTimeout(() => {
      toast.classList.add('removing');
      toast.addEventListener('animationend', () => toast.remove());
    }, 3500);
  }

  // ─── Validate short code ───
  if (!shortCode) {
    titleEl.textContent = 'No link specified';
    longUrlEl.textContent = 'Please select a link from the dashboard.';
    statsRow.classList.add('hidden');
    if (chartGridMain) chartGridMain.classList.add('hidden');
    if (chartGridSecondary) chartGridSecondary.classList.add('hidden');
    return;
  }

  titleEl.textContent = shortCode;

  // ─── Extract short code from a shortUrl value ───
  // The backend returns just the code (e.g. "abc123"), not a full URL.
  function extractCode(shortUrl) {
    if (!shortUrl) return '';
    try {
      return new URL(shortUrl).pathname.replace(/^\//, '');
    } catch {
      // Not a full URL — it IS the short code
      return shortUrl;
    }
  }

  // ─── Fetch URL info ───
  async function loadUrlInfo() {
    try {
      const res = await fetch('/api/v1/urls');
      if (!res.ok) return;
      const urls = await res.json();
      const match = urls.find(u => extractCode(u.shortUrl) === shortCode);
      if (match) {
        longUrlEl.textContent = match.longUrl || '';
        badgeEl.textContent = match.active ? 'Active' : 'Inactive';
        badgeEl.className = `badge ${match.active ? 'badge-active' : 'badge-inactive'}`;
        badgeEl.style.display = 'inline-flex';
      }
    } catch {
      // Non-critical, ignore
    }
  }

  // ─── Fetch Analytics ───
  async function loadAnalytics() {
    pageLoader.classList.remove('hidden');
    statsRow.classList.add('hidden');
    if (chartGridMain) chartGridMain.style.display = 'none';
    if (chartGridSecondary) chartGridSecondary.style.display = 'none';

    try {
      const res = await fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}`);
      if (!res.ok) throw new Error(`Server error (${res.status})`);

      const data = await res.json();

      if (!data || (Array.isArray(data) && data.length === 0)) {
        emptyState.classList.remove('hidden');
        return;
      }

      const records = Array.isArray(data) ? data : [data];

      // Sort by date
      records.sort((a, b) => new Date(a.clickDate) - new Date(b.clickDate));

      // Aggregate totals
      let totalClicks   = 0;
      let totalDesktop  = 0;
      let totalMobile   = 0;
      let totalTablet   = 0;

      // Aggregate browser counts
      let totalChrome = 0;
      let totalSafari = 0;
      let totalFirefox = 0;
      let totalBrowserOther = 0;

      // Aggregate OS counts
      let totalWindows = 0;
      let totalMacos = 0;
      let totalAndroid = 0;
      let totalIos = 0;
      let totalOsOther = 0;

      records.forEach(r => {
        totalClicks  += r.clickCount || 0;
        totalDesktop += r.deviceDesktopCount || 0;
        totalMobile  += r.deviceMobileCount || 0;
        totalTablet  += r.deviceTabletCount || 0;

        // Browsers
        totalChrome += r.browserChromeCount || 0;
        totalSafari += r.browserSafariCount || 0;
        totalFirefox += r.browserFirefoxCount || 0;
        totalBrowserOther += r.browserOtherCount || 0;

        // OS
        totalWindows += r.osWindowsCount || 0;
        totalMacos += r.osMacosCount || 0;
        totalAndroid += r.osAndroidCount || 0;
        totalIos += r.osIosCount || 0;
        totalOsOther += r.osOtherCount || 0;
      });

      const totalDevices = totalDesktop + totalMobile + totalTablet;

      // Update stat cards
      statTotal.textContent   = totalClicks.toLocaleString();
      statDesktop.textContent = totalDevices > 0
        ? Math.round((totalDesktop / totalDevices) * 100) + '%'
        : '0%';
      statMobile.textContent  = totalDevices > 0
        ? Math.round((totalMobile / totalDevices) * 100) + '%'
        : '0%';
      statTablet.textContent  = totalDevices > 0
        ? Math.round((totalTablet / totalDevices) * 100) + '%'
        : '0%';

      statsRow.classList.remove('hidden');
      if (chartGridMain) chartGridMain.style.display = '';
      if (chartGridSecondary) chartGridSecondary.style.display = '';

      renderClicksChart(records);
      renderDeviceChart(totalDesktop, totalMobile, totalTablet);
      renderBrowserChart(totalChrome, totalSafari, totalFirefox, totalBrowserOther);
      renderOsChart(totalWindows, totalMacos, totalAndroid, totalIos, totalOsOther);

    } catch (err) {
      console.error('Analytics error:', err);
      showToast('Failed to load analytics data.', 'error');
      emptyState.classList.remove('hidden');
    } finally {
      pageLoader.classList.add('hidden');
    }
  }

  // ─── Chart.js Global Defaults ───
  function configureChartDefaults() {
    if (typeof Chart === 'undefined') return;

    Chart.defaults.color = 'rgba(240, 240, 245, 0.6)';
    Chart.defaults.font.family = "'Inter', sans-serif";
    Chart.defaults.font.size = 12;
    Chart.defaults.plugins.legend.labels.usePointStyle = true;
    Chart.defaults.plugins.legend.labels.padding = 20;
    Chart.defaults.responsive = true;
    Chart.defaults.maintainAspectRatio = true;
  }

  // ─── Clicks Over Time (Line Chart) ───
  function renderClicksChart(records) {
    const ctx = document.getElementById('clicksChart');
    if (!ctx) return;

    const labels = records.map(r => {
      const d = new Date(r.clickDate);
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });
    const data = records.map(r => r.clickCount || 0);

    if (clicksChart) clicksChart.destroy();

    const gradient = ctx.getContext('2d').createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(102, 126, 234, 0.3)');
    gradient.addColorStop(1, 'rgba(102, 126, 234, 0.0)');

    clicksChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Clicks',
          data,
          borderColor: '#667eea',
          backgroundColor: gradient,
          borderWidth: 2.5,
          pointBackgroundColor: '#667eea',
          pointBorderColor: '#667eea',
          pointRadius: 4,
          pointHoverRadius: 6,
          pointHoverBackgroundColor: '#fff',
          pointHoverBorderWidth: 2,
          fill: true,
          tension: 0.4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(18, 18, 26, 0.95)',
            titleColor: '#f0f0f5',
            bodyColor: '#f0f0f5',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 8,
            displayColors: false,
            callbacks: {
              title: (items) => items[0].label,
              label: (item) => `${item.parsed.y} clicks`
            }
          }
        },
        scales: {
          x: {
            grid: {
              color: 'rgba(255,255,255,0.04)',
              drawBorder: false
            },
            ticks: {
              maxRotation: 45,
              maxTicksLimit: 10
            }
          },
          y: {
            beginAtZero: true,
            grid: {
              color: 'rgba(255,255,255,0.04)',
              drawBorder: false
            },
            ticks: {
              precision: 0
            }
          }
        },
        interaction: {
          mode: 'index',
          intersect: false
        }
      }
    });
  }

  // ─── Device Breakdown (Doughnut Chart) ───
  function renderDeviceChart(desktop, mobile, tablet) {
    const ctx = document.getElementById('deviceChart');
    if (!ctx) return;

    if (deviceChart) deviceChart.destroy();

    const total = desktop + mobile + tablet;

    if (total === 0) {
      return;
    }

    deviceChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Desktop', 'Mobile', 'Tablet'],
        datasets: [{
          data: [desktop, mobile, tablet],
          backgroundColor: [
            'rgba(102, 126, 234, 0.85)',
            'rgba(52, 211, 153, 0.85)',
            'rgba(251, 191, 36, 0.85)'
          ],
          borderColor: [
            'rgba(102, 126, 234, 1)',
            'rgba(52, 211, 153, 1)',
            'rgba(251, 191, 36, 1)'
          ],
          borderWidth: 1,
          hoverOffset: 6,
          spacing: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        cutout: '65%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 16,
              usePointStyle: true,
              pointStyleWidth: 10
            }
          },
          tooltip: {
            backgroundColor: 'rgba(18, 18, 26, 0.95)',
            titleColor: '#f0f0f5',
            bodyColor: '#f0f0f5',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 8,
            callbacks: {
              label: (item) => {
                const pct = total > 0 ? Math.round((item.raw / total) * 100) : 0;
                return `${item.label}: ${item.raw} (${pct}%)`;
              }
            }
          }
        }
      }
    });
  }

  // ─── Browser Breakdown (Doughnut Chart) ───
  function renderBrowserChart(chrome, safari, firefox, other) {
    const ctx = document.getElementById('browserChart');
    if (!ctx) return;

    if (browserChart) browserChart.destroy();

    const total = chrome + safari + firefox + other;

    browserChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Chrome', 'Safari', 'Firefox', 'Other'],
        datasets: [{
          data: [chrome, safari, firefox, other],
          backgroundColor: [
            'rgba(59, 130, 246, 0.85)',
            'rgba(249, 115, 22, 0.85)',
            'rgba(239, 68, 68, 0.85)',
            'rgba(156, 163, 175, 0.85)'
          ],
          borderColor: [
            'rgba(59, 130, 246, 1)',
            'rgba(249, 115, 22, 1)',
            'rgba(239, 68, 68, 1)',
            'rgba(156, 163, 175, 1)'
          ],
          borderWidth: 1,
          hoverOffset: 6,
          spacing: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        cutout: '65%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 16,
              usePointStyle: true,
              pointStyleWidth: 10
            }
          },
          tooltip: {
            backgroundColor: 'rgba(18, 18, 26, 0.95)',
            titleColor: '#f0f0f5',
            bodyColor: '#f0f0f5',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 8,
            callbacks: {
              label: (item) => {
                const pct = total > 0 ? Math.round((item.raw / total) * 100) : 0;
                return `${item.label}: ${item.raw} (${pct}%)`;
              }
            }
          }
        }
      }
    });
  }

  // ─── OS Breakdown (Doughnut Chart) ───
  function renderOsChart(windows, macos, android, ios, other) {
    const ctx = document.getElementById('osChart');
    if (!ctx) return;

    if (osChart) osChart.destroy();

    const total = windows + macos + android + ios + other;

    osChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Windows', 'macOS', 'Android', 'iOS', 'Other'],
        datasets: [{
          data: [windows, macos, android, ios, other],
          backgroundColor: [
            'rgba(6, 182, 212, 0.85)',
            'rgba(168, 85, 247, 0.85)',
            'rgba(34, 197, 94, 0.85)',
            'rgba(236, 72, 153, 0.85)',
            'rgba(107, 114, 128, 0.85)'
          ],
          borderColor: [
            'rgba(6, 182, 212, 1)',
            'rgba(168, 85, 247, 1)',
            'rgba(34, 197, 94, 1)',
            'rgba(236, 72, 153, 1)',
            'rgba(107, 114, 128, 1)'
          ],
          borderWidth: 1,
          hoverOffset: 6,
          spacing: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        cutout: '65%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 16,
              usePointStyle: true,
              pointStyleWidth: 10
            }
          },
          tooltip: {
            backgroundColor: 'rgba(18, 18, 26, 0.95)',
            titleColor: '#f0f0f5',
            bodyColor: '#f0f0f5',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 8,
            callbacks: {
              label: (item) => {
                const pct = total > 0 ? Math.round((item.raw / total) * 100) : 0;
                return `${item.label}: ${item.raw} (${pct}%)`;
              }
            }
          }
        }
      }
    });
  }

  // ─── Init ───
  configureChartDefaults();
  loadUrlInfo();
  loadAnalytics();

})();
