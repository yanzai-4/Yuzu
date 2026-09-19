import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { App } from './App';
import { initBackend } from './api/client';
import { onRequestError } from './api/http';
import { reportError } from './stores/errors';
import { syncThemeAttribute } from './stores/prefs';

// Every failed request becomes a toast plus an entry in the Trace tab's error log.
onRequestError((error) => reportError(error.apiError, 'request', `${error.method} ${error.path}`));
syncThemeAttribute();

void initBackend().then(() => {
  const root = document.getElementById('root');
  if (!root) throw new Error('Missing #root element');
  createRoot(root).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
