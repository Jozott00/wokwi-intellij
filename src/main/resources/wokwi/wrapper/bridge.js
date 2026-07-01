(function () {
  const iframe = document.querySelector('iframe');
  const loadingMessage = document.querySelector('#loading-message');
  const offlineMessage = document.querySelector('#offline-message');
  const intellij = window.__WokwiIntellij = window.__WokwiIntellij || {};
  const missedMessages = [];
  const pendingMessagesForWokwi = [];
  let wokwiPort = null;
  let wokwiLoaded = false;

  function decodeBase64ToUint8Array(base64) {
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes;
  }

  function prepareMessageForWokwi(data) {
    if (data?.command !== 'start' || !Array.isArray(data.chips)) {
      return data;
    }

    return {
      ...data,
      chips: data.chips.map((chip) => {
        if (!chip.binaryBase64) {
          return chip;
        }

        const { binaryBase64, ...chipSettings } = chip;
        return {
          ...chipSettings,
          binary: decodeBase64ToUint8Array(binaryBase64),
        };
      }),
    };
  }

  function postToIntellij(type, data) {
    const pkg = JSON.stringify({ type, data });
    if (intellij.__postMessageToPipe === undefined) {
      missedMessages.push(pkg);
      return;
    }

    intellij.__postMessageToPipe(pkg);
  }

  function sendMissedMessages() {
    while (missedMessages.length > 0) {
      intellij.__postMessageToPipe(missedMessages.shift());
    }
  }

  function postToWokwi(data) {
    const message = prepareMessageForWokwi(data);
    if (!wokwiPort) {
      pendingMessagesForWokwi.push(message);
      return;
    }

    wokwiPort.postMessage(message);
  }

  function sendPendingMessagesToWokwi() {
    while (pendingMessagesForWokwi.length > 0) {
      wokwiPort.postMessage(pendingMessagesForWokwi.shift());
    }
  }

  intellij.__receiveMessageFromPipe = (_type, data) => postToWokwi(data);

  iframe.addEventListener('load', () => {
    postToIntellij('meta', { msg: 'frameLoaded' });
    if (!wokwiLoaded) {
      loadingMessage.classList.add('hidden');
      offlineMessage.classList.remove('hidden');
    }
  });

  iframe.src = iframe.getAttribute('data-src');

  window.addEventListener('message', (event) => {
    const message = event.data;
    if (message?.command !== 'start' || !message.port) {
      return;
    }

    wokwiLoaded = true;
    loadingMessage.classList.add('hidden');
    offlineMessage.classList.add('hidden');
    const { port, ...messageBody } = message;
    wokwiPort = port;
    wokwiPort.onmessage = (event) => postToIntellij('wokwi', event.data);
    wokwiPort.onmessageerror = (event) => {
      console.error('Message error on Wokwi port', event);
    };
    postToIntellij('wokwi', messageBody);
    sendPendingMessagesToWokwi();
  });

  window.addEventListener('IdeReady', sendMissedMessages);
})();
