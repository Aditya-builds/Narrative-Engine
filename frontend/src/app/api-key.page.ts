import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OpenAiKeyStore } from './openai-key.store';

@Component({
  selector: 'app-api-key-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './api-key.page.html'
})
export class ApiKeyPage {
  private readonly keys = inject(OpenAiKeyStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly error = signal('');
  readonly hasKey = signal(this.keys.has());
  draft = '';

  save(): void {
    const key = this.draft.trim();
    this.error.set('');
    if (!this.keys.looksValid(key)) {
      this.error.set('Paste a full OpenAI API key. It should start with sk-');
      return;
    }
    this.keys.set(key);
    this.hasKey.set(true);
    this.draft = '';
    this.goOn();
  }

  continueExisting(): void {
    if (!this.keys.has()) {
      this.error.set('Paste a full OpenAI API key. It should start with sk-');
      return;
    }
    this.goOn();
  }

  clear(): void {
    this.keys.clear();
    this.hasKey.set(false);
    this.draft = '';
    this.error.set('');
  }

  private goOn(): void {
    const raw = this.route.snapshot.queryParamMap.get('returnUrl') || '/';
    const returnUrl = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/';
    void this.router.navigateByUrl(returnUrl);
  }
}
