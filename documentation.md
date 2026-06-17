# VectorLink — Application Features & UI Reference

This document provides a comprehensive list of every feature, page component, state, and interaction currently implemented in **VectorLink** (high-performance URL shortener). Use this as a reference guide when designing the screens in Stitch or other UI tools.

---

## 1. Authentication & Account Management

### A. Landing Page Navbar states
- **Guest State**: Displays links to `Home`, `Dashboard`, `Login`, and `Register`.
- **Authenticated State**: Displays `Home`, `Dashboard`, and a `Sign Out` button. 
- **Persistent Session**: Uses secure `HttpOnly` JWT cookies valid for **30 days**.

### B. Sign In Page (`login.html`)
- **Visual Design**: Dark glassmorphism card.
- **Fields**:
  - Email Address (input)
  - Password (input)
- **Buttons**:
  - `Sign In` (primary submit button with loading spinner)
  - `Google` (OAuth2 social login button)
- **Links**: `Don't have an account? Sign Up` (links to `register.html`).
- **States**:
  - Success message banner.
  - Error message banner (e.g. invalid credentials or OAuth2 failure).

### C. Sign Up Page (`register.html`)
- **Visual Design**: Dark glassmorphism card.
- **Fields**:
  - Email Address (input)
  - Password (input, min 6 characters requirement)
  - Confirm Password (input)
- **Buttons**:
  - `Sign Up` (primary submit button)
- **Links**: `Already have an account? Sign In` (links to `login.html`).
- **States**:
  - Passwords mismatch error validation.
  - Success redirect countdown banner.

---

## 2. Core URL Shortening Page (`index.html`)

### A. Hero Section & Shortener Form
- **Headline**: "Shorten Your Links, Expand Your <Gradient>Reach</Gradient>"
- **Sub-headline**: Description of analytics, speed, and customization features.
- **Form Inputs**:
  - **Destination URL**: Target URL input field (validated for `http://` or `https://` prefix).
  - **Custom Alias (Optional)**: Input field to specify a custom short URL path (validated: 3-30 alphanumeric characters/hyphens).
  - **Expiry (TTL)**: Dropdown select menu with preset durations (1 hour, 1 day, 7 days, 30 days, Custom).
  - **Custom Expiry Date Picker**: Datetime-local selector (visible only if "Custom" is chosen from the Expiry dropdown).
- **Advanced Options Toggle**: Expandable section exposing:
  - **Password Protection**: Input field to set a password to gate redirections.
  - **Fallback URL**: Input field to specify where to redirect users if the link expires or is manually deactivated.
- **Action Button**: `Shorten` (primary action with button text spinner).

### B. Interactive Features
- **Global Stats Banner**: Animated counters that count up on page load:
  - Total Links Created
  - Total Clicks Processed
  - Satisfied Users
- **Result Card**: Appears below the form upon successful shortening:
  - Clickable Shortened URL (e.g., `localhost:8080/abc123`).
  - Copy Button (interactive, copies link, updates icon to checkmark, displays toast success).
  - Target destination URL preview.
  - QR Code image (automatically generated).
  - Expiration timestamp.
  - Custom Alias (if configured).

---

## 3. Link Management Dashboard (`dashboard.html`)

### A. Dashboard Header & Stats Row
- **Summary Cards**:
  - **Total Links**: Total number of short URLs created by the user.
  - **Total Clicks**: Accumulated clicks across all of the user's links.
  - **Active Links**: Count of links currently enabled.

### B. Toolbar
- **Search Bar**: Real-time filter input (filters list instantly as user types, searching both short codes and destination URLs).
- **Quick Action**: `+ New Link` button (shortcuts back to shortening form on landing page).

### C. URL Cards List
- **Active State / Grid Card**:
  - Clickable Short URL (opens target link in a new tab).
  - Destination URL (truncated preview with mouseover tooltip).
  - **Status Badges**:
    - `Active` (green) / `Inactive` (gray).
    - `🔒 Protected` (warning gold, shown if password protection is set).
    - `🏷️ Alias` (accent purple, displays the custom alias if set).
    - `Fallback: Link` (preview of fallback redirect, shown if set).
  - **Meta details**: Click counter, Expiration date.
  - **Actions Group**:
    - `Copy` button (copies full short URL).
    - `QR Code` button (opens modal overlay showing a scanable QR code).
    - `Analytics` button (redirects to the detailed analytics view).
    - `Active State Slider`: Interactive toggle switch to activate/deactivate the link in real-time.
    - `Delete` button (trash icon, triggers confirm delete modal).

### D. Overlay Modals
- **QR Code Modal**: Displays the QR code image centered with a "Close" button.
- **Delete Confirmation Modal**: Asks "Delete Link?" warning that deleting is permanent and deletes all analytics history, with "Cancel" and "Delete" actions.

---

## 4. Analytics Visualization Page (`analytics.html`)

### A. Header & Key Stats
- **Header**: Back link arrow to return to dashboard; Displays the short code/alias name, the target destination URL, and its active status badge.
- **Grid Summary**:
  - Total Clicks Card
  - Desktop % Card
  - Mobile % Card
  - Tablet % Card

### B. Visualizations & Interactive Map
- **Global Visitor Distribution**: Interactive flat world map (powered by `jsVectorMap`) showing the geographical distribution of visitors. Hovering over countries displays a custom tool-tip showing the country name and its click count, styled with dark glassmorphic borders. It dynamically colors countries using a purple-to-accent color scale heatmap based on click volume.
- **Clicks Over Time**: Line chart showing click count distribution over dates.
- **Device Breakdown**: Doughnut chart showing percentage of Desktop vs Mobile vs Tablet.
- **Hourly Traffic Pattern**: Line/area chart showing click counts aggregated by the hour of the day (00:00 to 23:00 UTC) to analyze peak traffic hours.
- **Top Traffic Sources (Referrers)**: Horizontal bar chart displaying the top referring websites or direct access count.
- **Browser Breakdown**: Doughnut chart showing Chrome, Safari, Firefox, Edge, and Other.
- **OS Breakdown**: Doughnut chart showing Windows, macOS, Android, iOS, and Other.
- **Offline Resiliency**: If the user's browser fails to load Chart.js or jsVectorMap, the page displays warning toasts and raw numeric statistics fallback values without throwing script execution errors.

### C. Motion Design & Premium Interaction
- **Staggered Entrance Animations**: All cards, charts, and maps slide up into view with a smooth cubic-bezier (`cubic-bezier(0.16, 1, 0.3, 1)`) transition. Staggered delays create a professional dashboard cascade effect.
- **Interactive Card Lift**: Elements lift up by `2px` on mouse hover, glowing with a soft border highlight and accent shadow.

---

## 5. Password Gate Screen (`password.html`)

- **Trigger**: Shown when a user visits a password-protected short URL.
- **Visual Design**: Sleek centered dark glassmorphism card.
- **Content**: Prompts "This link is password protected. Enter the password to access."
- **Fields**:
  - Password (input)
- **Buttons**:
  - `Unlock Link` (primary button)
- **Flow**:
  - Submits to `POST /api/v1/urls/verify-password`.
  - Sets a secure temporary cookie (`vlink_pass_{shortCode}`) on success and redirects user to target destination.
  - Shows error banner if password is incorrect.

---

## 6. System Observability & Infrastructure

- **Prometheus Scrape Endpoint**: `/actuator/prometheus` (tracks JVM health, redirection latency histograms, cache hit/miss counters, Kafka buffer size).
- **Grafana Visualization**: Provisioned dashboard displaying real-time application health metrics.
- **Load Test Scripts**: Ready-to-use k6 test scripts (smoke, stress, spike scenarios).
- **Resilience Buffering**: Clicking redirects logs data asynchronously to Kafka. If Kafka is down, events buffer in memory (or dead-letter logs).
