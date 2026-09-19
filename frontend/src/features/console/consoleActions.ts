import { listModels, putLlmKey, testLlm, updateLlmSettings } from '../../api/client';
import type { LlmTestResult, UpdateLlmSettingsRequest } from '../../api/types';
import { setLlmSettings, setModels } from '../../stores/settings';
import { pushToast } from '../../stores/ui';

/** v0.0.4 🍊 Saves provider, base URL and tiers (PUT /api/settings/llm). */
export async function saveLlmSettings(body: UpdateLlmSettingsRequest): Promise<boolean> {
  try {
    setLlmSettings(await updateLlmSettings(body));
    pushToast({ tone: 'success', title: 'Model settings saved' });
    return true;
  } catch {
    return false;
  }
}

/** v0.0.4 🍊 Stores the API key (PUT /api/settings/llm/key); only the mask comes back. */
export async function saveApiKey(apiKey: string): Promise<boolean> {
  try {
    const view = await putLlmKey(apiKey);
    setLlmSettings(view);
    pushToast({ tone: 'success', title: 'API key stored', message: view.apiKeyMasked ? `Saved as ${view.apiKeyMasked}` : undefined });
    return true;
  } catch {
    return false;
  }
}

/** v0.0.4 🍊 Loads the provider's model ids into the datalist (GET /api/settings/llm/models). */
export async function fetchModels(): Promise<number | null> {
  try {
    const models = await listModels();
    setModels(models);
    return models.length;
  } catch {
    return null;
  }
}

/** v0.0.4 🍊 Pings every tier (POST /api/settings/llm/test). */
export async function runLlmTest(): Promise<LlmTestResult | null> {
  try {
    return await testLlm();
  } catch {
    return null;
  }
}
