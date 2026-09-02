import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { OpenAiKeySource, OpenAiKeyStore } from './openai-key.store';

@Component({
  selector: 'app-api-key-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './api-key.page.html'
})
export class ApiKeyPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly keys = inject(OpenAiKeyStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly error = signal('');
  readonly checking = signal(true);
  readonly hasServerKey = signal(false);
  readonly serverCheckFailed = signal(false);
  readonly hasKey = signal(this.keys.has());
  readonly source = signal<OpenAiKeySource>(this.keys.source());
  draft = '';

  ngOnInit(): void {
    this.api
      .getLlmConfig()
      .pipe(
        catchError(() => {
          this.serverCheckFailed.set(true);
          return of({ has_server_api_key: false });
        })
      )
      .subscribe((cfg) => {
        this.hasServerKey.set(!!cfg.has_server_api_key);
        this.checking.set(false);
      });
  }

  useServer(): void {
    this.error.set('');
    if (!this.hasServerKey()) {
      this.error.set('The agent has no OPENAI_API_KEY in .env. Paste your own key instead.');
      return;
    }
    this.keys.useServerKey();
    this.source.set('env');
    this.hasKey.set(false);
    this.draft = '';
    this.goOn();
  }

  save(): void {
    const key = this.draft.trim();
    this.error.set('');
    if (!this.keys.looksValid(key)) {
      this.error.set('Paste a full OpenAI API key. It should start with sk-');
      return;
    }
    this.keys.set(key);
    this.source.set('user');
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
    this.source.set('');
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
