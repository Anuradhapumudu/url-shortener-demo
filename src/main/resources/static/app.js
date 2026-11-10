	const ICON_COPY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>';
	const ICON_TRASH = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/></svg>';

	const $ = (id) => document.getElementById(id);
	let links = [];
	let lastClicks = {};
	let pendingDelete = null;
	let pendingTimer = null;
	let toastTimer = null;

	function toast(message) {
		const t = $('toast');
		t.textContent = message;
		t.classList.add('show');
		clearTimeout(toastTimer);
		toastTimer = setTimeout(() => t.classList.remove('show'), 1800);
	}

	function ago(ts) {
		const s = Math.max(0, (Date.now() - new Date(ts).getTime()) / 1000);
		if (s < 60) return 'just now';
		if (s < 3600) return Math.floor(s / 60) + ' min ago';
		if (s < 86400) return Math.floor(s / 3600) + ' h ago';
		if (s < 7 * 86400) return Math.floor(s / 86400) + ' d ago';
		return new Date(ts).toLocaleDateString();
	}

	function hostOf(url) {
		try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return url; }
	}

	// Accept "example.com/page" as well as full URLs
	function normalise(value) {
		const v = value.trim();
		if (!v) return '';
		return /^[a-z][a-z0-9+.-]*:\/\//i.test(v) ? v : 'https://' + v;
	}

	async function copyText(text) {
		try {
			await navigator.clipboard.writeText(text);
		} catch (e) {
			// clipboard API needs https or localhost
			const ta = document.createElement('textarea');
			ta.value = text;
			document.body.appendChild(ta);
			ta.select();
			document.execCommand('copy');
			ta.remove();
		}
		toast('Link copied');
	}

	function favicon(host) {
		const box = document.createElement('div');
		box.className = 'fav';
		const clean = (host || '').replace(/^www\./, '');
		box.textContent = (clean ? clean.charAt(0) : 'L').toUpperCase();
		return box;
	}

	function renderRow(l) {
		const row = document.createElement('div');
		row.className = 'row';
		if (lastClicks[l.code] !== undefined && l.clickCount > lastClicks[l.code]) {
			row.classList.add('flash');
			requestAnimationFrame(() => setTimeout(() => row.classList.remove('flash'), 300));
		}

		const info = document.createElement('div');
		info.className = 'info';
		const a = document.createElement('a');
		a.href = l.shortUrl;
		a.target = '_blank';
		a.rel = 'noopener';
		a.textContent = l.shortUrl.replace(/^https?:\/\//, '');
		const dest = document.createElement('span');
		dest.className = 'dest';
		dest.textContent = l.longUrl;
		dest.title = l.longUrl;
		const meta = document.createElement('span');
		meta.className = 'meta';
		meta.textContent = 'Created ' + ago(l.createdAt) + (l.lastClickedAt ? ' · last opened ' + ago(l.lastClickedAt) : '');
		info.append(a, dest, meta);

		const clicks = document.createElement('div');
		clicks.className = 'clicks';
		const b = document.createElement('b');
		b.textContent = l.clickCount;
		const label = document.createElement('span');
		label.textContent = l.clickCount === 1 ? 'click' : 'clicks';
		clicks.append(b, label);

		const actions = document.createElement('div');
		actions.className = 'actions';
		const copy = document.createElement('button');
		copy.type = 'button';
		copy.className = 'icon-btn';
		copy.title = 'Copy link';
		copy.setAttribute('aria-label', 'Copy ' + l.shortUrl);
		copy.innerHTML = ICON_COPY;
		copy.onclick = () => copyText(l.shortUrl);

		const del = document.createElement('button');
		del.type = 'button';
		const confirming = pendingDelete === l.code;
		del.className = 'icon-btn danger' + (confirming ? ' confirm' : '');
		del.title = confirming ? 'Click again to delete' : 'Delete link';
		del.setAttribute('aria-label', confirming ? 'Confirm delete' : 'Delete ' + l.shortUrl);
		if (confirming) del.textContent = 'Delete?';
		else del.innerHTML = ICON_TRASH;
		del.onclick = () => onDelete(l.code);
		actions.append(copy, del);

		row.append(favicon(hostOf(l.longUrl)), info, clicks, actions);
		return row;
	}

	function render() {
		const q = $('search').value.trim().toLowerCase();
		const shown = q
			? links.filter((l) => (l.shortUrl + ' ' + l.longUrl).toLowerCase().includes(q))
			: links;

		$('count').textContent = links.length ? '(' + links.length + ')' : '';
		const empty = $('empty');
		if (links.length && !shown.length) {
			empty.innerHTML = '<strong>No matches</strong>No links match your search.';
		} else {
			empty.innerHTML = '<strong>No links yet</strong>Paste a link above and press Shorten. It will appear here with its click count.';
		}
		const rows = shown.map(renderRow);
		$('list').replaceChildren(...(rows.length ? rows : [empty]));
		lastClicks = Object.fromEntries(links.map((l) => [l.code, l.clickCount]));
	}

	function askForKey() {
		const key = prompt('This server requires an API key:');
		if (key) sessionStorage.setItem('apiKey', key.trim());
		return Boolean(key);
	}

	async function api(url, options = {}, interactive = true) {
		const send = () => fetch(url, { ...options, headers: { ...(options.headers || {}), 'X-API-Key': sessionStorage.getItem('apiKey') || '' } });
		let res = await send();
		if (res.status === 401 && interactive && askForKey()) {
			res = await send();
		}
		return res;
	}

	function renderKeyRequired(invalid = false) {
		const box = document.createElement('div');
		box.className = 'empty';
		box.innerHTML = invalid
			? '<strong>Invalid API Key</strong>The provided API key was not accepted.<br><br>'
			: '<strong>API Key Required</strong>Enter your API key to view and manage existing links.<br><br>';
		const btn = document.createElement('button');
		btn.className = 'btn small';
		btn.type = 'button';
		btn.textContent = 'Enter API Key';
		btn.onclick = () => { if (askForKey()) load(); };
		box.appendChild(btn);
		$('list').replaceChildren(box);
	}

	async function load() {
		try {
			const hadKey = Boolean(sessionStorage.getItem('apiKey'));
			const res = await api('/api/links', {}, false);
			if (res.status === 401) {
				if (hadKey) sessionStorage.removeItem('apiKey');
				renderKeyRequired(hadKey);
				return;
			}
			if (!res.ok) return;
			links = await res.json();
			render();
		} catch (e) {
			// server restarting; the next tick retries
		}
	}

	async function onDelete(code) {
		clearTimeout(pendingTimer);
		if (pendingDelete !== code) {
			pendingDelete = code;
			pendingTimer = setTimeout(() => { pendingDelete = null; render(); }, 3000);
			render();
			return;
		}
		pendingDelete = null;
		const res = await api('/api/links/' + encodeURIComponent(code), { method: 'DELETE' });
		if (res.ok) toast('Link deleted');
		if ($('shortLink').dataset.code === code) resetComposer();
		load();
	}

	function showError(message) {
		$('error').textContent = message;
		$('error').hidden = false;
		$('hint').hidden = true;
	}

	function resetComposer() {
		$('result').hidden = true;
		$('error').hidden = true;
		$('hint').hidden = false;
		$('longUrl').value = '';
		$('longUrl').focus();
	}

	function renderQr(text) {
		const box = $('qr');
		box.replaceChildren();
		if (window.QRCode) {
			new QRCode(box, { text, width: 112, height: 112, colorDark: '#131828', colorLight: '#ffffff', correctLevel: QRCode.CorrectLevel.M });
		}
		box.parentElement.hidden = !window.QRCode;
	}

	$('form').addEventListener('submit', async (e) => {
		e.preventDefault();
		const longUrl = normalise($('longUrl').value);
		$('error').hidden = true;
		if (!longUrl) {
			showError('Paste a link first, for example www.example.com/page');
			$('longUrl').focus();
			return;
		}

		const button = $('submit');
		button.disabled = true;
		button.textContent = 'Shortening…';
		try {
			const res = await api('/api/links', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ longUrl })
			});
			const body = await res.json().catch(() => ({}));
			if (!res.ok) {
				$('result').hidden = true;
				if (res.status === 401) {
					showError('Valid API key required to create links.');
				} else {
					showError(body.error || 'That link could not be shortened. Check it is a valid web address and try again.');
				}
				return;
			}

			const link = $('shortLink');
			link.href = body.shortUrl;
			link.textContent = body.shortUrl.replace(/^https?:\/\//, '');
			link.dataset.code = body.code;
			$('resultOrig').textContent = body.longUrl;
			$('resultOrig').title = body.longUrl;
			$('open').href = body.shortUrl;
			$('copy').onclick = () => copyText(body.shortUrl);
			renderQr(body.shortUrl);
			$('hint').hidden = true;
			$('result').hidden = false;
			$('longUrl').value = '';
			load();
		} catch (err) {
			showError('Could not reach the server. It may be waking up, so wait a few seconds and try again.');
		} finally {
			button.disabled = false;
			button.textContent = 'Shorten';
		}
	});

	$('another').addEventListener('click', resetComposer);
	$('search').addEventListener('input', render);

	load();
	setInterval(load, 5000);
