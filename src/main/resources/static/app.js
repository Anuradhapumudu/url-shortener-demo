	const ICON_COPY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>';
	const ICON_TRASH = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/></svg>';

	const $ = (id) => document.getElementById(id);
	let links = [];
	let lastClicks = {};
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
		const fallback = document.createElement('span');
		fallback.className = 'fav-fallback';
		fallback.textContent = (clean ? clean.charAt(0) : 'L').toUpperCase();
		box.append(fallback);
		if (!clean) return box;

		const image = document.createElement('img');
		image.alt = '';
		image.loading = 'lazy';
		image.decoding = 'async';
		image.referrerPolicy = 'no-referrer';
		image.src = 'https://www.google.com/s2/favicons?domain=' + encodeURIComponent(clean) + '&sz=64';
		image.onload = () => { fallback.hidden = true; };
		image.onerror = () => { image.remove(); };
		box.append(image);
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
		del.className = 'icon-btn danger';
		del.title = 'Delete link';
		del.setAttribute('aria-label', 'Delete ' + l.shortUrl);
		del.innerHTML = ICON_TRASH;
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

	function getKey() {
		try {
			const urlParams = new URLSearchParams(window.location.search);
			const paramKey = urlParams.get('key');
			if (paramKey && paramKey.trim()) {
				setKey(paramKey.trim());
				urlParams.delete('key');
				const cleanSearch = urlParams.toString();
				const newUrl = window.location.pathname + (cleanSearch ? '?' + cleanSearch : '') + window.location.hash;
				window.history.replaceState({}, '', newUrl);
				return paramKey.trim();
			}
		} catch (e) {}
		try {
			return localStorage.getItem('apiKey') || sessionStorage.getItem('apiKey') || '';
		} catch (e) {
			return '';
		}
	}

	function setKey(key) {
		try {
			if (key && key.trim()) {
				localStorage.setItem('apiKey', key.trim());
				sessionStorage.setItem('apiKey', key.trim());
			} else {
				localStorage.removeItem('apiKey');
				sessionStorage.removeItem('apiKey');
			}
		} catch (e) {}
		updateKeyBadge();
	}

	function updateKeyBadge() {
		const badge = $('keyBadge');
		if (!badge) return;
		const key = getKey();
		if (key) {
			badge.className = 'key-pill active';
			badge.textContent = '✓ Key Active';
			badge.title = 'API key active. Click to change or clear.';
		} else {
			badge.className = 'key-pill';
			badge.textContent = '🔑 Enter API Key';
			badge.title = 'Click to configure API key';
		}
	}

	function askForKey() {
		const current = getKey();
		const promptMsg = current
			? 'Current API key: ' + current.substring(0, 8) + '...\nEnter new API key (or leave empty to clear):'
			: 'Enter API key (from .env SHORT_LINK_API_KEY):';
		const key = prompt(promptMsg, current || '');
		if (key !== null) {
			setKey(key.trim());
			return Boolean(key.trim());
		}
		return false;
	}

	async function api(url, options = {}, interactive = true) {
		const send = () => fetch(url, {
			...options,
			headers: {
				...(options.headers || {}),
				'X-API-Key': getKey()
			}
		});
		let res = await send();
		if (res.status === 401 && interactive && askForKey()) {
			res = await send();
		}
		return res;
	}

	function renderKeyRequired(invalid = false) {
		$('count').textContent = '';
		const box = document.createElement('div');
		box.className = 'empty';
		const title = document.createElement('strong');
		title.textContent = invalid ? 'Invalid API Key' : 'API Key Required';
		const desc = document.createElement('p');
		desc.style.cssText = 'margin: 6px 0 16px; font-size: 13px; color: var(--muted);';
		desc.textContent = invalid
			? 'The configured API key was rejected. Paste the key from .env below:'
			: 'Paste your API key below (or open with ?key=YOUR_KEY) to unlock link management:';

		const form = document.createElement('div');
		form.style.cssText = 'display: flex; gap: 8px; max-width: 440px; margin: 0 auto; flex-wrap: wrap; justify-content: center;';
		const input = document.createElement('input');
		input.type = 'password';
		input.placeholder = 'Paste X-API-Key';
		input.style.cssText = 'flex: 1 1 240px; padding: 8px 12px; border-radius: 8px; border: 1px solid var(--line); background: var(--surface); color: var(--ink); font-size: 13px;';
		const btn = document.createElement('button');
		btn.className = 'btn small';
		btn.type = 'button';
		btn.textContent = 'Unlock';

		const unlock = () => {
			const val = input.value.trim();
			if (val) {
				setKey(val);
				load();
			}
		};
		btn.onclick = unlock;
		input.onkeydown = (e) => { if (e.key === 'Enter') unlock(); };

		form.append(input, btn);
		box.append(title, desc, form);
		$('list').replaceChildren(box);
		updateKeyBadge();
	}

	async function load() {
		try {
			updateKeyBadge();
			const hadKey = Boolean(getKey());
			const res = await api('/api/links', {}, false);
			if (res.status === 401) {
				if (hadKey) setKey('');
				renderKeyRequired(hadKey);
				return;
			}
			if (!res.ok) return;
			links = await res.json();
			render();
			updateKeyBadge();
		} catch (e) {
			// server restarting; the next tick retries
		}
	}

	async function onDelete(code) {
		const res = await api('/api/links/' + encodeURIComponent(code), { method: 'DELETE' });
		if (res.ok) toast('Link deleted');
		else toast('Could not delete link');
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
	if ($('keyBadge')) {
		$('keyBadge').addEventListener('click', () => {
			if (askForKey()) load();
		});
	}

	load();
	setInterval(load, 5000);
