(function () {
  'use strict';

  const shortenForm = document.getElementById('shorten-form');
  const longUrlInput = document.getElementById('long-url');
  const customCodeInput = document.getElementById('custom-code');
  const shortenBtn = document.getElementById('shorten-btn');
  const statusBox = document.getElementById('status');
  const resultBox = document.getElementById('result');
  const resultLink = document.getElementById('result-link');
  const copyBtn = document.getElementById('copy-btn');

  const statsForm = document.getElementById('stats-form');
  const statsCodeInput = document.getElementById('stats-code');
  const statsBtn = document.getElementById('stats-btn');
  const statsOut = document.getElementById('stats-out');

  // Show a message in the status box. Uses textContent, never innerHTML.
  function showStatus(message, kind) {
    statusBox.textContent = message;
    statusBox.className = 'status ' + kind;
    statusBox.hidden = false;
  }

  function hideStatus() {
    statusBox.hidden = true;
    statusBox.textContent = '';
  }

  // Turn an HTTP status and the server's plain-text body into a message for the user.
  function messageFor(status, body) {
    const text = (body || '').trim();
    switch (status) {
      case 400:
        return text || 'Check the long URL and the custom code, then try again.';
      case 409:
        return text || 'That custom code is already taken. Try another one.';
      case 429:
        return 'Too many requests. Wait a minute and try again.';
      default:
        return 'Something went wrong (HTTP ' + status + '). Try again.';
    }
  }

  shortenForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    hideStatus();
    resultBox.hidden = true;

    const longUrl = longUrlInput.value.trim();
    const customCode = customCodeInput.value.trim();

    if (!longUrl) {
      showStatus('Enter a long URL first.', 'error');
      longUrlInput.focus();
      return;
    }

    const payload = { longUrl: longUrl };
    if (customCode) {
      payload.customCode = customCode;
    }

    shortenBtn.disabled = true;
    shortenBtn.textContent = 'Shortening...';
    try {
      const response = await fetch('/api/shorten', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const body = await response.text();

      if (response.status === 201) {
        const shortUrl = body.trim();
        resultLink.href = shortUrl;
        resultLink.textContent = shortUrl;
        resultBox.hidden = false;
        showStatus('Short link created.', 'info');
        shortenForm.reset();
      } else {
        showStatus(messageFor(response.status, body), 'error');
      }
    } catch (err) {
      showStatus('Cannot reach the server. Check that the app is running.', 'error');
    } finally {
      shortenBtn.disabled = false;
      shortenBtn.textContent = 'Shorten';
    }
  });

  copyBtn.addEventListener('click', async function () {
    const text = resultLink.textContent;
    try {
      await navigator.clipboard.writeText(text);
      copyBtn.textContent = 'Copied';
    } catch (err) {
      // Clipboard can be blocked. Select the link so the user can copy it manually.
      const range = document.createRange();
      range.selectNodeContents(resultLink);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      copyBtn.textContent = 'Press Ctrl+C';
    }
    setTimeout(function () { copyBtn.textContent = 'Copy'; }, 2000);
  });

  // Stats response format: "ShortCode: x, LongUrl: y, Clicks: n"
  const STATS_PATTERN = /^ShortCode: (.*), LongUrl: (.*), Clicks: (\d+)$/;

  function renderStats(code, body) {
    const match = body.match(STATS_PATTERN);
    statsOut.textContent = '';
    if (!match) {
      statsOut.textContent = body;
      statsOut.hidden = false;
      return;
    }
    const wrapper = document.createElement('div');

    const clicks = document.createElement('div');
    clicks.className = 'big';
    clicks.textContent = match[3];
    const clicksLabel = document.createElement('div');
    clicksLabel.textContent = 'clicks';
    clicksLabel.className = 'label';

    const list = document.createElement('dl');
    [['Short code', match[1]], ['Long URL', match[2]]].forEach(function (pair) {
      const dt = document.createElement('dt');
      dt.textContent = pair[0];
      const dd = document.createElement('dd');
      dd.textContent = pair[1];
      list.append(dt, dd);
    });

    wrapper.append(clicks, clicksLabel, list);
    statsOut.append(wrapper);
    statsOut.hidden = false;
  }

  statsForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    const code = statsCodeInput.value.trim();
    if (!code) {
      statsOut.textContent = 'Enter a short code first.';
      statsOut.hidden = false;
      statsCodeInput.focus();
      return;
    }

    statsBtn.disabled = true;
    try {
      const response = await fetch('/api/stats/' + encodeURIComponent(code));
      const body = await response.text();
      if (response.status === 200) {
        renderStats(code, body.trim());
      } else if (response.status === 404) {
        statsOut.textContent = 'No link found for "' + code + '".';
        statsOut.hidden = false;
      } else {
        statsOut.textContent = messageFor(response.status, body);
        statsOut.hidden = false;
      }
    } catch (err) {
      statsOut.textContent = 'Cannot reach the server. Check that the app is running.';
      statsOut.hidden = false;
    } finally {
      statsBtn.disabled = false;
    }
  });
})();
