/* Только браузерный адаптер микрофона/IndexedDB/HTMLAudio. UI и правила — Kotlin commonMain. */
(() => {
  'use strict';
  let recorder = null, stream = null, player = null, sessionId = null, writes = Promise.resolve(), stopped = null;
  let openPromise;
  const db = () => openPromise ||= new Promise((resolve, reject) => {
    const req = indexedDB.open('3rdbrain-audio-v1', 1);
    req.onupgradeneeded = () => {
      req.result.createObjectStore('sessions', { keyPath: 'id' });
      req.result.createObjectStore('chunks', { keyPath: ['id', 'index'] });
    };
    req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error);
  });
  const all = async store => new Promise(async (resolve, reject) => {
    try { const request = (await db()).transaction(store).objectStore(store).getAll(); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }
    catch (error) { reject(error); }
  });
  const put = async (store, value) => {
    const database = await db();
    return new Promise((resolve, reject) => {
      const tx = database.transaction(store, 'readwrite'); tx.objectStore(store).put(value);
      tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error || new Error('Не удалось сохранить аудио в браузере'));
    });
  };
  const errorText = e => 'ERROR:' + (e?.message || String(e));
  const release = () => { stream?.getTracks().forEach(t => t.stop()); stream = null; };
  const upload = async base => {
    await writes;
    const sessions = (await all('sessions')).sort((a,b) => a.created - b.created);
    const saved = sessions[0]; if (!saved) throw new Error('Нет записи для восстановления');
    const chunks = (await all('chunks')).filter(x => x.id === saved.id).sort((a,b) => a.index - b.index);
    if (!chunks.length) throw new Error('Восстановленная запись пуста; исходный сеанс оставлен в браузере');
    const blob = new Blob(chunks.map(x => x.blob), { type: saved.mime });
    const form = new FormData(); const ext = saved.mime.includes('mp4') ? 'm4a' : saved.mime.includes('ogg') ? 'ogg' : 'webm';
    form.append('audio', blob, 'capture.' + ext);
    const response = await fetch(base + '/api/captures/audio', { method: 'POST', headers: { 'X-3rdBrain-Client': 'web', 'X-Capture-Id': saved.id }, body: form });
    const text = await response.text();
    if (!response.ok) { let message = text; try { message = JSON.parse(text).error || text; } catch {} throw new Error(message); }
    // Проверяем квитанцию до удаления источника. Повтор запроса идемпотентен на сервере.
    const receipt = JSON.parse(text); if (receipt.id !== saved.id) throw new Error('Неверное подтверждение сохранения записи');
    const database = await db();
    await new Promise((resolve, reject) => {
      const tx = database.transaction(['sessions', 'chunks'], 'readwrite'); tx.objectStore('sessions').delete(saved.id);
      chunks.forEach(chunk => tx.objectStore('chunks').delete([chunk.id, chunk.index]));
      tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error);
    });
    return text;
  };
  globalThis.thirdBrainPlatform = {
    baseUrl: () => location.port === '8080' ? 'http://127.0.0.1:8787' : location.origin,
    consent: () => { try { return localStorage.getItem('thirdbrain.mic-consent') === 'yes'; } catch { return false; } },
    pending: async () => (await all('sessions')).length > 0 && (!recorder || recorder.state === 'inactive'),
    phase: () => recorder?.state === 'recording' ? 'recording' : recorder?.state === 'paused' ? 'paused' : 'idle',
    start: async () => {
      try {
        if (recorder && recorder.state !== 'inactive') return 'ok';
        if ((await all('sessions')).length) throw new Error('Есть несохранённая запись. Нажмите «Повторить отправку»');
        if (!navigator.mediaDevices?.getUserMedia || !globalThis.MediaRecorder) throw new Error('Микрофон недоступен. Откройте приложение на localhost в современном браузере');
        player?.pause();
        stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const mime = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus'].find(t => MediaRecorder.isTypeSupported(t));
        recorder = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
        sessionId = crypto.randomUUID(); let index = 0, byteCount = 0, persistError;
        await put('sessions', { id: sessionId, mime: recorder.mimeType || mime || 'audio/webm', created: Date.now() });
        writes = Promise.resolve();
        const ownId = sessionId, ownRecorder = recorder;
        recorder.ondataavailable = e => {
          if (!e.data?.size) return;
          const chunk = { id: ownId, index: index++, blob: e.data };
          writes = writes.then(() => put('chunks', chunk)).catch(error => { persistError = error; });
          byteCount += e.data.size;
          if (byteCount >= 60 * 1024 * 1024 && ownRecorder.state !== 'inactive') ownRecorder.stop();
        };
        stopped = new Promise((resolve, reject) => {
          ownRecorder.onstop = async () => { release(); await writes; persistError ? reject(persistError) : resolve(); };
          ownRecorder.onerror = e => { if (ownRecorder.state !== 'inactive') ownRecorder.stop(); else release(); };
        });
        stopped.catch(() => {}); // Ошибка будет показана при сохранении; не теряем частично сохранённые фрагменты.
        recorder.start(1000);
        try { localStorage.setItem('thirdbrain.mic-consent', 'yes'); } catch {}
        return 'ok';
      } catch (e) { release(); return errorText(e); }
    },
    pause: () => { try { if (recorder?.state !== 'recording') throw new Error('Запись не активна'); recorder.pause(); return 'ok'; } catch(e) { return errorText(e); } },
    resume: () => { try { if (recorder?.state !== 'paused') throw new Error('Запись не на паузе'); recorder.resume(); return 'ok'; } catch(e) { return errorText(e); } },
    stop: async base => {
      try { if (recorder?.state !== 'inactive') recorder?.stop(); await stopped; recorder = null; return await upload(base); }
      catch (e) { return errorText(e); }
    },
    recover: async base => { try { return await upload(base); } catch(e) { return errorText(e); } },
    play: async (url, from, rate) => {
      try {
        player?.pause(); player = new Audio(url); player.preservesPitch = true; player.playbackRate = rate;
        await new Promise((resolve, reject) => { player.onloadedmetadata = resolve; player.onerror = () => reject(new Error('Аудиоисточник недоступен')); });
        player.currentTime = Math.max(0, Math.min(Number.isFinite(player.duration) ? player.duration : from, from));
        await player.play(); return 'ok';
      } catch(e) { return errorText(e); }
    },
    stopAudio: () => { player?.pause(); player = null; },
  };
  addEventListener('beforeunload', event => {
    if (recorder && recorder.state !== 'inactive') { event.preventDefault(); event.returnValue = ''; }
  });
})();
