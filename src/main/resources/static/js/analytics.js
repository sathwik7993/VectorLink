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
  const chartGridTraffic = document.getElementById('chartGridTraffic');
  const chartGridSecondary = document.getElementById('chartGridSecondary');
  const mapSection    = document.getElementById('mapSection');

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
  let hourlyChart = null;
  let referrerChart = null;
  let mapInstance = null;
  let totalClicksGlobal = 0;
  let eventSource = null;
  let geojson = {
    type: 'FeatureCollection',
    features: []
  };

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
      if (res.status === 401) {
        window.location.href = '/login.html';
        return;
      }
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
    if (chartGridTraffic) chartGridTraffic.style.display = 'none';
    if (chartGridSecondary) chartGridSecondary.style.display = 'none';
    if (mapSection) mapSection.style.display = 'none';

    try {
      const [res, countriesRes, referrersRes, hourlyRes, locationsRes] = await Promise.all([
        fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}`),
        fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}/countries`),
        fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}/referrers`),
        fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}/hourly`),
        fetch(`/api/v1/analytics/${encodeURIComponent(shortCode)}/locations`)
      ]);

      if (res.status === 401) {
        window.location.href = '/login.html';
        return;
      }
      if (!res.ok) throw new Error(`Server error (${res.status})`);

      const [data, countryData, referrerData, hourlyData, locationData] = await Promise.all([
        res.json(),
        countriesRes.ok ? countriesRes.json() : {},
        referrersRes.ok ? referrersRes.json() : {},
        hourlyRes.ok ? hourlyRes.json() : {},
        locationsRes.ok ? locationsRes.json() : []
      ]);

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
      if (chartGridTraffic) chartGridTraffic.style.display = '';
      if (chartGridSecondary) chartGridSecondary.style.display = '';
      if (mapSection) mapSection.style.display = '';

      if (typeof Chart !== 'undefined') {
        renderClicksChart(records);
        renderDeviceChart(totalDesktop, totalMobile, totalTablet);
        renderBrowserChart(totalChrome, totalSafari, totalFirefox, totalBrowserOther);
        renderOsChart(totalWindows, totalMacos, totalAndroid, totalIos, totalOsOther);
        renderHourlyChart(hourlyData);
        renderReferrerChart(referrerData);
      } else {
        console.warn('Chart.js is not loaded. Showing numeric statistics only.');
        showToast('Chart.js could not be loaded. Showing raw numbers only.', 'warning');
      }

      totalClicksGlobal = totalClicks;

      if (typeof maplibregl !== 'undefined') {
        renderWorldMap(locationData);
      } else {
        console.warn('MapLibre GL is not loaded. Skipping map.');
      }

      connectToClickStream();

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

    Chart.defaults.color = 'rgba(30, 30, 30, 0.6)';
    if (Chart.defaults.scale && Chart.defaults.scale.grid) {
      Chart.defaults.scale.grid.color = 'rgba(0, 0, 0, 0.06)';
    }
    if (Chart.defaults.plugins && Chart.defaults.plugins.tooltip) {
      Chart.defaults.plugins.tooltip.backgroundColor = 'rgba(255, 255, 255, 0.98)';
      Chart.defaults.plugins.tooltip.titleColor = '#1d1d1f';
      Chart.defaults.plugins.tooltip.bodyColor = '#515154';
      Chart.defaults.plugins.tooltip.borderColor = 'rgba(0, 0, 0, 0.08)';
      Chart.defaults.plugins.tooltip.borderWidth = 1;
    }
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
    gradient.addColorStop(0, 'rgba(0, 113, 227, 0.15)');
    gradient.addColorStop(1, 'transparent');

    clicksChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Clicks',
          data,
          borderColor: '#0071E3',
          backgroundColor: gradient,
          borderWidth: 2.5,
          pointBackgroundColor: '#0071E3',
          pointBorderColor: '#0071E3',
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
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
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
              color: 'rgba(0, 0, 0, 0.06)',
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
              color: 'rgba(0, 0, 0, 0.06)',
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
            'rgba(0, 113, 227, 0.85)',
            'rgba(52, 199, 89, 0.85)',
            'rgba(255, 149, 0, 0.85)'
          ],
          borderColor: [
            'rgba(0, 113, 227, 1)',
            'rgba(52, 199, 89, 1)',
            'rgba(255, 149, 0, 1)'
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
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
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
            'rgba(0, 122, 255, 0.85)',
            'rgba(255, 149, 0, 0.85)',
            'rgba(255, 59, 48, 0.85)',
            'rgba(142, 142, 147, 0.85)'
          ],
          borderColor: [
            'rgba(0, 122, 255, 1)',
            'rgba(255, 149, 0, 1)',
            'rgba(255, 59, 48, 1)',
            'rgba(142, 142, 147, 1)'
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
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
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
            'rgba(50, 173, 230, 0.85)',
            'rgba(175, 82, 222, 0.85)',
            'rgba(52, 199, 89, 0.85)',
            'rgba(255, 45, 85, 0.85)',
            'rgba(142, 142, 147, 0.85)'
          ],
          borderColor: [
            'rgba(50, 173, 230, 1)',
            'rgba(175, 82, 222, 1)',
            'rgba(52, 199, 89, 1)',
            'rgba(255, 45, 85, 1)',
            'rgba(142, 142, 147, 1)'
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
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
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

  // ─── Hourly Traffic Pattern (Line/Area Chart) ───
  function renderHourlyChart(hourlyData) {
    const ctx = document.getElementById('hourlyChart');
    if (!ctx) return;

    if (hourlyChart) {
      hourlyChart.destroy();
      hourlyChart = null;
    }

    const labels = [];
    const data = [];
    for (let i = 0; i < 24; i++) {
      labels.push(`${String(i).padStart(2, '0')}:00`);
      data.push(hourlyData[i] || hourlyData[String(i)] || 0);
    }

    const gradient = ctx.getContext('2d').createLinearGradient(0, 0, 0, 200);
    gradient.addColorStop(0, 'rgba(52, 199, 89, 0.15)');
    gradient.addColorStop(1, 'transparent');

    hourlyChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Clicks',
          data,
          borderColor: '#34C759',
          backgroundColor: gradient,
          borderWidth: 2.5,
          pointBackgroundColor: '#34C759',
          pointRadius: 2,
          pointHoverRadius: 5,
          fill: true,
          tension: 0.3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
            borderWidth: 1,
            padding: 10,
            cornerRadius: 8,
            callbacks: {
              label: (item) => `${item.parsed.y} clicks`
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(0, 0, 0, 0.06)', drawBorder: false },
            ticks: { maxTicksLimit: 12 }
          },
          y: {
            beginAtZero: true,
            grid: { color: 'rgba(0, 0, 0, 0.06)', drawBorder: false },
            ticks: { precision: 0 }
          }
        }
      }
    });
  }

  // ─── Top Traffic Sources (Horizontal Bar Chart) ───
  function renderReferrerChart(referrerData) {
    const ctx = document.getElementById('referrerChart');
    if (!ctx) return;

    if (referrerChart) {
      referrerChart.destroy();
      referrerChart = null;
    }

    const entries = Object.entries(referrerData || {}).sort((a, b) => b[1] - a[1]);
    const labels = entries.map(e => e[0]);
    const data = entries.map(e => e[1]);

    if (labels.length === 0) {
      labels.push('No Referrer Data');
      data.push(0);
    }

    const finalLabels = labels.slice(0, 7);
    const finalData = data.slice(0, 7);

    const gradient = ctx.getContext('2d').createLinearGradient(0, 0, 300, 0);
    gradient.addColorStop(0, 'rgba(175, 82, 222, 0.85)'); // premium purple
    gradient.addColorStop(1, 'rgba(0, 113, 227, 0.85)');  // premium blue

    referrerChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: finalLabels,
        datasets: [{
          data: finalData,
          backgroundColor: gradient,
          borderColor: 'rgba(0, 0, 0, 0.05)',
          borderWidth: 1,
          borderRadius: 4,
          barThickness: 16
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            titleColor: '#1d1d1f',
            bodyColor: '#515154',
            borderColor: 'rgba(0, 0, 0, 0.08)',
            borderWidth: 1,
            padding: 10,
            cornerRadius: 8
          }
        },
        scales: {
          x: {
            beginAtZero: true,
            grid: { color: 'rgba(0, 0, 0, 0.06)', drawBorder: false },
            ticks: { precision: 0 }
          },
          y: {
            grid: { display: false, drawBorder: false },
            ticks: {
              color: 'rgba(30, 30, 30, 0.8)',
              font: { size: 11 }
            }
          }
        }
      }
    });
  }

  // ─── Global Visitor Distribution (3D Globe visualization using Globe.gl) ───
  const countryCoordinates = {
    'US': { lat: 37.0902, lng: -95.7129, name: 'United States' },
    'IN': { lat: 20.5937, lng: 78.9629, name: 'India' },
    'GB': { lat: 55.3781, lng: -3.4360, name: 'United Kingdom' },
    'CA': { lat: 56.1304, lng: -106.3468, name: 'Canada' },
    'DE': { lat: 51.1657, lng: 10.4515, name: 'Germany' },
    'FR': { lat: 46.2276, lng: 2.2137, name: 'France' },
    'JP': { lat: 36.2048, lng: 138.2529, name: 'Japan' },
    'CN': { lat: 35.8617, lng: 104.1954, name: 'China' },
    'BR': { lat: -14.2350, lng: -51.9253, name: 'Brazil' },
    'AU': { lat: -25.2744, lng: 133.7751, name: 'Australia' },
    'RU': { lat: 61.5240, lng: 105.3188, name: 'Russia' },
    'ZA': { lat: -30.5595, lng: 22.9375, name: 'South Africa' },
    'MX': { lat: 23.6345, lng: -102.5528, name: 'Mexico' },
    'ES': { lat: 40.4637, lng: -3.7492, name: 'Spain' },
    'IT': { lat: 41.8719, lng: 12.5674, name: 'Italy' },
    'NL': { lat: 52.1326, lng: 5.2913, name: 'Netherlands' },
    'SE': { lat: 60.1282, lng: 18.6435, name: 'Sweden' },
    'CH': { lat: 46.8182, lng: 8.2275, name: 'Switzerland' },
    'SG': { lat: 1.3521, lng: 103.8198, name: 'Singapore' },
    'MY': { lat: 4.2105, lng: 101.9758, name: 'Malaysia' },
    'ID': { lat: -0.7893, lng: 113.9213, name: 'Indonesia' },
    'KR': { lat: 35.9078, lng: 127.7669, name: 'South Korea' },
    'NZ': { lat: -40.9006, lng: 174.8860, name: 'New Zealand' },
    'IE': { lat: 53.4129, lng: -8.2439, name: 'Ireland' },
    'BE': { lat: 50.5039, lng: 4.4699, name: 'Belgium' },
    'AT': { lat: 47.5162, lng: 14.5501, name: 'Austria' },
    'DK': { lat: 56.2639, lng: 9.5018, name: 'Denmark' },
    'NO': { lat: 60.4720, lng: 8.4689, name: 'Norway' },
    'FI': { lat: 61.9241, lng: 25.7482, name: 'Finland' },
    'PL': { lat: 51.9194, lng: 19.1451, name: 'Poland' },
    'TR': { lat: 38.9637, lng: 35.2433, name: 'Turkey' },
    'SA': { lat: 23.8859, lng: 45.0792, name: 'Saudi Arabia' },
    'AE': { lat: 23.4241, lng: 53.8478, name: 'United Arab Emirates' },
    'AR': { lat: -38.4161, lng: -63.6167, name: 'Argentina' },
    'CO': { lat: 4.5709, lng: -74.2973, name: 'Colombia' },
    'CL': { lat: -35.6751, lng: -71.5430, name: 'Chile' },
    'PE': { lat: -9.1900, lng: -75.0152, name: 'Peru' },
    'VE': { lat: 6.4238, lng: -66.5897, name: 'Venezuela' },
    'EG': { lat: 26.8206, lng: 30.8025, name: 'Egypt' },
    'NG': { lat: 9.0820, lng: 8.6753, name: 'Nigeria' },
    'KE': { lat: -0.0236, lng: 37.9062, name: 'Kenya' },
    'TH': { lat: 15.8700, lng: 100.9925, name: 'Thailand' },
    'VN': { lat: 14.0583, lng: 108.2772, name: 'Vietnam' },
    'PH': { lat: 12.8797, lng: 121.7740, name: 'Philippines' },
    'PK': { lat: 30.3753, lng: 69.3451, name: 'Pakistan' },
    'BD': { lat: 23.6850, lng: 90.3563, name: 'Bangladesh' },
    'UA': { lat: 48.3794, lng: 31.1656, name: 'Ukraine' },
    'PT': { lat: 39.3999, lng: -8.2245, name: 'Portugal' },
    'GR': { lat: 39.0742, lng: 21.8243, name: 'Greece' },
    'IL': { lat: 31.0461, lng: 34.8516, name: 'Israel' }
  };

  function createSquarePolygon(lng, lat, size = 0.6) {
    const half = size / 2;
    return [
      [lng - half, lat - half],
      [lng + half, lat - half],
      [lng + half, lat + half],
      [lng - half, lat + half],
      [lng - half, lat - half]
    ];
  }

  function renderWorldMap(locationData) {
    const mapContainer = document.getElementById('world-map');
    if (!mapContainer) return;

    mapContainer.innerHTML = '';

    // Override MapLibre GL popup classes for light theme
    if (!document.getElementById('maplibre-light-overrides')) {
      const style = document.createElement('style');
      style.id = 'maplibre-light-overrides';
      style.textContent = `
        .maplibregl-popup-content {
          background: #ffffff !important;
          border: 1px solid rgba(0, 0, 0, 0.08) !important;
          color: #1d1d1f !important;
          box-shadow: 0 4px 12px rgba(0,0,0,0.08) !important;
        }
        .maplibregl-popup-anchor-top .maplibregl-popup-tip {
          border-bottom-color: #ffffff !important;
        }
        .maplibregl-popup-anchor-bottom .maplibregl-popup-tip {
          border-top-color: #ffffff !important;
        }
        .maplibregl-popup-anchor-left .maplibregl-popup-tip {
          border-right-color: #ffffff !important;
        }
        .maplibregl-popup-anchor-right .maplibregl-popup-tip {
          border-left-color: #ffffff !important;
        }
      `;
      document.head.appendChild(style);
    }

    // Modern light background for map container
    mapContainer.style.background = '#ffffff';
    mapContainer.style.borderColor = 'rgba(0, 0, 0, 0.08)';

    geojson.features = [];

    // Map list to GeoJSON Features
    locationData.forEach(loc => {
      if (loc.latitude === null || loc.longitude === null) return;
      
      const count = loc.clickCount || 1;
      const height = count * 20000; // 20km height per click
      const color = count > 10 ? '#AF52DE' : '#0071E3';

      geojson.features.push({
        type: 'Feature',
        properties: {
          latitude: loc.latitude,
          longitude: loc.longitude,
          city: loc.city || 'Unknown',
          region: loc.region || 'Unknown',
          countryCode: loc.countryCode || 'Unknown',
          clickCount: count,
          lastClickTimestamp: loc.lastClickTimestamp || new Date().toISOString(),
          height: height,
          color: color
        },
        geometry: {
          type: 'Polygon',
          coordinates: [createSquarePolygon(loc.longitude, loc.latitude, 0.6)]
        }
      });
    });

    const mapStyle = {
      version: 8,
      sources: {
        'raster-tiles': {
          type: 'raster',
          tiles: [
            'https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png'
          ],
          tileSize: 256,
          attribution: '© CARTO'
        }
      },
      layers: [
        {
          id: 'simple-tiles',
          type: 'raster',
          source: 'raster-tiles',
          minzoom: 0,
          maxzoom: 19
        }
      ]
    };

    try {
      mapInstance = new maplibregl.Map({
        container: 'world-map',
        style: mapStyle,
        center: [0, 20],
        zoom: 1.2,
        pitch: 45,
        bearing: -10,
        maxZoom: 14,
        minZoom: 1
      });

      mapInstance.on('load', () => {
        mapInstance.addSource('clicks', {
          type: 'geojson',
          data: geojson
        });

        mapInstance.addLayer({
          id: 'clicks-3d',
          type: 'fill-extrusion',
          source: 'clicks',
          paint: {
            'fill-extrusion-color': ['get', 'color'],
            'fill-extrusion-height': ['get', 'height'],
            'fill-extrusion-base': 0,
            'fill-extrusion-opacity': 0.85
          }
        });

        const popup = new maplibregl.Popup({
          closeButton: false,
          closeOnClick: false
        });

        mapInstance.on('mousemove', 'clicks-3d', (e) => {
          mapInstance.getCanvas().style.cursor = 'pointer';

          const feature = e.features[0];
          const props = feature.properties;
          
          const country = props.countryCode;
          const city = props.city;
          const region = props.region;
          const count = props.clickCount;
          const lastClick = props.lastClickTimestamp;
          
          const total = totalClicksGlobal || count;
          const pct = total > 0 ? ((count / total) * 100).toFixed(1) : '100';
          const formattedTime = new Date(lastClick).toLocaleString();

          const html = `
            <div style="font-weight: 700; font-size: 13px; margin-bottom: 4px; color: #1d1d1f;">${city}, ${region} (${country})</div>
            <div style="font-size: 11px; color: #515154; margin-bottom: 2px;">Clicks: <strong style="color: #0071E3;">${count}</strong> (${pct}%)</div>
            <div style="font-size: 10px; color: #86868b;">Last Click: ${formattedTime}</div>
          `;

          popup.setLngLat(e.lngLat)
            .setHTML(html)
            .addTo(mapInstance);
        });

        mapInstance.on('mouseleave', 'clicks-3d', () => {
          mapInstance.getCanvas().style.cursor = '';
          popup.remove();
        });
      });
    } catch (err) {
      console.error('MapLibre GL initialization error:', err);
    }
  }

  function connectToClickStream() {
    if (eventSource) {
      eventSource.close();
    }

    eventSource = new EventSource(`/api/v1/analytics/${encodeURIComponent(shortCode)}/stream`);

    eventSource.addEventListener('click', (event) => {
      try {
        const clickData = JSON.parse(event.data);
        handleIncomingClick(clickData);
      } catch (err) {
        console.error('Error parsing stream event:', err);
      }
    });

    eventSource.addEventListener('error', () => {
      setTimeout(connectToClickStream, 5000);
    });
  }

  function handleIncomingClick(clickData) {
    // 1. Update total clicks card
    const totalVal = parseInt(statTotal.textContent.replace(/,/g, '')) || 0;
    statTotal.textContent = (totalVal + 1).toLocaleString();
    totalClicksGlobal = totalVal + 1;

    // 2. Update Device chart
    if (deviceChart && clickData.deviceType) {
      const label = clickData.deviceType;
      const idx = deviceChart.data.labels.findIndex(l => l.toLowerCase() === label.toLowerCase());
      if (idx !== -1) {
        deviceChart.data.datasets[0].data[idx]++;
        deviceChart.update();
      }
    }

    // 3. Update Browser chart
    if (browserChart && clickData.browser) {
      const idx = browserChart.data.labels.indexOf(clickData.browser);
      if (idx !== -1) {
        browserChart.data.datasets[0].data[idx]++;
        browserChart.update();
      } else {
        const otherIdx = browserChart.data.labels.indexOf('Other');
        if (otherIdx !== -1) {
          browserChart.data.datasets[0].data[otherIdx]++;
          browserChart.update();
        }
      }
    }

    // 4. Update OS chart
    if (osChart && clickData.operatingSystem) {
      const idx = osChart.data.labels.indexOf(clickData.operatingSystem);
      if (idx !== -1) {
        osChart.data.datasets[0].data[idx]++;
        osChart.update();
      } else {
        const otherIdx = osChart.data.labels.indexOf('Other');
        if (otherIdx !== -1) {
          osChart.data.datasets[0].data[otherIdx]++;
          osChart.update();
        }
      }
    }

    // 5. Update Clicks over time chart
    if (clicksChart) {
      const dateLabel = new Date(clickData.clickedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
      const idx = clicksChart.data.labels.indexOf(dateLabel);
      if (idx !== -1) {
        clicksChart.data.datasets[0].data[idx]++;
        clicksChart.update();
      } else {
        clicksChart.data.labels.push(dateLabel);
        clicksChart.data.datasets[0].data.push(1);
        clicksChart.update();
      }
    }

    // 6. Update Hourly chart
    if (hourlyChart) {
      const hour = new Date(clickData.clickedAt).getUTCHours();
      if (hourlyChart.data.datasets[0].data[hour] !== undefined) {
        hourlyChart.data.datasets[0].data[hour]++;
        hourlyChart.update();
      }
    }

    // 7. Update Referrer chart
    if (referrerChart) {
      const referrer = clickData.referrer || 'Direct';
      const idx = referrerChart.data.labels.indexOf(referrer);
      if (idx !== -1) {
        referrerChart.data.datasets[0].data[idx]++;
        referrerChart.update();
      } else if (referrerChart.data.labels.length < 7) {
        referrerChart.data.labels.push(referrer);
        referrerChart.data.datasets[0].data.push(1);
        referrerChart.update();
      }
    }

    // 8. Update 3D Map
    if (clickData.latitude !== null && clickData.longitude !== null) {
      // Find feature within 0.1 degree distance
      const tolerance = 0.1;
      let existingIdx = geojson.features.findIndex(f => 
        Math.abs(f.properties.latitude - clickData.latitude) < tolerance && 
        Math.abs(f.properties.longitude - clickData.longitude) < tolerance
      );

      if (existingIdx !== -1) {
        const feat = geojson.features[existingIdx];
        feat.properties.clickCount++;
        feat.properties.lastClickTimestamp = clickData.clickedAt;
        const targetHeight = feat.properties.clickCount * 20000;
        feat.properties.color = feat.properties.clickCount > 10 ? '#AF52DE' : '#0071E3';
        animateBarHeight(existingIdx, targetHeight);
      } else {
        // Create new feature rising from height 0 to 20000
        const newIdx = geojson.features.length;
        geojson.features.push({
          type: 'Feature',
          properties: {
            latitude: clickData.latitude,
            longitude: clickData.longitude,
            city: clickData.city || 'Unknown',
            region: clickData.region || 'Unknown',
            countryCode: clickData.countryCode || 'Unknown',
            clickCount: 1,
            lastClickTimestamp: clickData.clickedAt,
            height: 0,
            color: '#0071E3'
          },
          geometry: {
            type: 'Polygon',
            coordinates: [createSquarePolygon(clickData.longitude, clickData.latitude, 0.6)]
          }
        });
        animateBarHeight(newIdx, 20000);
      }
    }
  }

  function animateBarHeight(index, targetHeight) {
    const feature = geojson.features[index];
    if (!feature) return;

    const startHeight = feature.properties.height || 0;
    const duration = 800; // 800ms animation
    const startTime = performance.now();

    function step(now) {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const ease = 1 - Math.pow(1 - progress, 3); // Cubic Out Easing

      feature.properties.height = startHeight + (targetHeight - startHeight) * ease;

      if (mapInstance && mapInstance.getSource('clicks')) {
        mapInstance.getSource('clicks').setData(geojson);
      }

      if (progress < 1) {
        requestAnimationFrame(step);
      }
    }

    requestAnimationFrame(step);
  }

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
            <li><a href="dashboard.html">Dashboard</a></li>
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
      configureChartDefaults();
      loadUrlInfo();
      loadAnalytics();
    }
  }
  init();

})();
