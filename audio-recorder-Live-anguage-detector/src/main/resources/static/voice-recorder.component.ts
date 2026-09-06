const MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;

type ResultData = {
  transcription?: string;
  translation?: string;
  summary?: string;
  reportPath?: string;
};

type RecordingMode = 'audio' | 'video';

export class VoiceRecorderComponent {
  private isProcessingValue = false;
  private isRecordingValue = false;
  private statusValue = 'Ready';
  private errorValue = '';
  private resultValue: ResultData | null = null;
  private targetLangValue = 'en';
  private fileValue: File | null = null;
  private recordingModeValue: RecordingMode = 'audio';
  private mediaRecorder: MediaRecorder | null = null;
  private mediaChunks: BlobPart[] = [];
  private localStream: MediaStream | null = null;

  constructor(private root: HTMLElement) {
    this.render();
    this.bindEvents();
  }

  private bindEvents(): void {
    const fileInput = this.root.querySelector('#ro-file');
    const submitBtn = this.root.querySelector('#upload-file');
    const startBtn = this.root.querySelector('#start-recording');
    const stopBtn = this.root.querySelector('#stop-recording');
    const langSelect = this.root.querySelector('#target-lang');
    const modeSelect = this.root.querySelector('#recording-mode');

    fileInput?.addEventListener('change', (event) => {
      const target = event.target as HTMLInputElement;
      const file = target.files && target.files[0] ? target.files[0] : null;
      if (file && file.size > MAX_FILE_SIZE_BYTES) {
        this.fileValue = null;
        this.errorValue = 'The selected file is larger than the 100 MB limit.';
        this.statusValue = '';
        this.render();
        return;
      }
      this.fileValue = file;
    });

    submitBtn?.addEventListener('click', () => this.uploadFile());
    startBtn?.addEventListener('click', () => this.startRecording());
    stopBtn?.addEventListener('click', () => this.stopRecording());

    langSelect?.addEventListener('change', (event) => {
      const target = event.target as HTMLSelectElement;
      this.targetLangValue = target.value;
    });

    modeSelect?.addEventListener('change', (event) => {
      const target = event.target as HTMLSelectElement;
      this.recordingModeValue = (target.value as RecordingMode) || 'audio';
    });
  }

  private render(): void {
    this.root.innerHTML = `
      <main class="card">
        <h1>RO Record & Report</h1>
        <p>Choose audio or video recording, capture the RO file, and generate one simple report.</p>

        <div class="controls">
          <div>
            <label for="recording-mode">Recording mode</label>
            <select id="recording-mode">
              <option value="audio" ${this.recordingModeValue === 'audio' ? 'selected' : ''}>Audio recording</option>
              <option value="video" ${this.recordingModeValue === 'video' ? 'selected' : ''}>Video recording</option>
            </select>
          </div>

          <button id="start-recording" type="button" ${this.isProcessingValue || this.isRecordingValue ? 'disabled' : ''}>
            ${this.isRecordingValue ? 'Recording...' : 'Start recording'}
          </button>
          <button id="stop-recording" type="button" ${!this.isRecordingValue || this.isProcessingValue ? 'disabled' : ''}>
            Stop recording
          </button>

          <div>
            <label for="ro-file">Or upload a file</label>
            <input id="ro-file" type="file" accept=".wav,.mp3,.m4a,.ogg,.webm,.mp4" />
          </div>

          <div>
            <label for="target-lang">Target language</label>
            <select id="target-lang">
              <option value="en" ${this.targetLangValue === 'en' ? 'selected' : ''}>English</option>
              <option value="hi" ${this.targetLangValue === 'hi' ? 'selected' : ''}>Hindi</option>
              <option value="mr" ${this.targetLangValue === 'mr' ? 'selected' : ''}>Marathi</option>
              <option value="kn" ${this.targetLangValue === 'kn' ? 'selected' : ''}>Kannada</option>
              <option value="te" ${this.targetLangValue === 'te' ? 'selected' : ''}>Telugu</option>
              <option value="ml" ${this.targetLangValue === 'ml' ? 'selected' : ''}>Malayalam</option>
              <option value="ta" ${this.targetLangValue === 'ta' ? 'selected' : ''}>Tamil</option>
              <option value="bn" ${this.targetLangValue === 'bn' ? 'selected' : ''}>Bengali</option>
              <option value="gu" ${this.targetLangValue === 'gu' ? 'selected' : ''}>Gujarati</option>
              <option value="pa" ${this.targetLangValue === 'pa' ? 'selected' : ''}>Punjabi</option>
              <option value="or" ${this.targetLangValue === 'or' ? 'selected' : ''}>Odia</option>
              <option value="es" ${this.targetLangValue === 'es' ? 'selected' : ''}>Spanish</option>
              <option value="fr" ${this.targetLangValue === 'fr' ? 'selected' : ''}>French</option>
              <option value="de" ${this.targetLangValue === 'de' ? 'selected' : ''}>German</option>
            </select>
          </div>

          <button id="upload-file" type="button" ${this.isProcessingValue || (!this.fileValue && !this.isRecordingValue) ? 'disabled' : ''}>
            ${this.isProcessingValue ? 'Generating report...' : 'Generate report'}
          </button>
        </div>

        <div class="status ${this.errorValue ? 'error' : this.statusValue && !this.isProcessingValue ? 'success' : ''}">
          ${this.errorValue || this.statusValue || 'Ready'}
        </div>

        ${this.resultValue ? `
          <section class="output">
            <h2>Report</h2>
            <div class="result-block">
              <strong>Transcription</strong>
              <pre>${this.resultValue.transcription || 'N/A'}</pre>
            </div>
            <div class="result-block">
              <strong>Translation</strong>
              <pre>${this.resultValue.translation || 'N/A'}</pre>
            </div>
            <div class="result-block">
              <strong>Summary</strong>
              <pre>${this.resultValue.summary || 'N/A'}</pre>
            </div>
            <div class="result-block">
              <strong>PDF</strong>
              <pre>${this.resultValue.reportPath || 'N/A'}</pre>
            </div>
            <div class="result-block">
              <strong>Saved media</strong>
              <pre>${this.resultValue.savedMediaPath || 'N/A'}</pre>
            </div>
          </section>
        ` : ''}
      </main>
    `;

    this.bindEvents();
  }

  private startRecording(): void {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      this.errorValue = 'This browser does not support media recording.';
      this.render();
      return;
    }

    const constraints: MediaStreamConstraints = this.recordingModeValue === 'video'
      ? { audio: true, video: true }
      : { audio: true };

    navigator.mediaDevices.getUserMedia(constraints)
      .then((stream) => {
        this.localStream = stream;
        const mimeType = this.recordingModeValue === 'video'
          ? (MediaRecorder.isTypeSupported('video/webm;codecs=vp9,opus') ? 'video/webm;codecs=vp9,opus' : 'video/webm')
          : (MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4');

        this.mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
        this.mediaChunks = [];
        this.mediaRecorder.ondataavailable = (event) => {
          if (event.data && event.data.size > 0) {
            this.mediaChunks.push(event.data);
          }
        };

        this.mediaRecorder.start();
        this.isRecordingValue = true;
        this.errorValue = '';
        this.statusValue = `${this.recordingModeValue === 'video' ? 'Video' : 'Audio'} recording started.`;
        this.render();
      })
      .catch(() => {
        this.errorValue = 'Microphone or camera access was denied or unavailable.';
        this.render();
      });
  }

  private stopRecording(): void {
    if (!this.mediaRecorder) {
      return;
    }

    this.mediaRecorder.onstop = () => {
      const mimeType = this.mediaRecorder?.mimeType || (this.recordingModeValue === 'video' ? 'video/webm' : 'audio/webm');
      const blob = new Blob(this.mediaChunks, { type: mimeType });
      const extension = this.recordingModeValue === 'video' ? 'webm' : 'webm';
      const fileName = `recorded-${this.recordingModeValue}.${extension}`;
      this.fileValue = new File([blob], fileName, { type: mimeType });
      this.isRecordingValue = false;
      this.statusValue = `${this.recordingModeValue === 'video' ? 'Video' : 'Audio'} recording captured.`;
      this.render();

      if (this.localStream) {
        this.localStream.getTracks().forEach((track) => track.stop());
        this.localStream = null;
      }
    };

    this.mediaRecorder.stop();
  }

  private uploadFile(): void {
    const selectedFile = this.fileValue;
    if (!selectedFile) {
      this.errorValue = 'Please choose a file or record audio/video before generating the report.';
      this.render();
      return;
    }

    if (selectedFile.size > MAX_FILE_SIZE_BYTES) {
      this.errorValue = 'The selected file is larger than the 100 MB limit.';
      this.render();
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile, selectedFile.name);
    formData.append('targetLang', this.targetLangValue);
    formData.append('mediaType', this.recordingModeValue);

    this.isProcessingValue = true;
    this.statusValue = 'Uploading media and generating report...';
    this.errorValue = '';
    this.render();

    fetch('/api/audio/voice/upload', {
      method: 'POST',
      body: formData
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`);
        }
        return response.json();
      })
      .then((result: ResultData) => {
        this.resultValue = result;
        this.isProcessingValue = false;
        this.statusValue = 'Report generated successfully.';
        this.render();
      })
      .catch((error: Error) => {
        this.isProcessingValue = false;
        this.errorValue = error.message || 'Error generating the report.';
        this.render();
      });
  }
}

const mountApp = () => {
  const root = document.getElementById('voice-recorder-app');
  if (root) {
    (window as any).VoiceRecorderComponent = VoiceRecorderComponent;
    new VoiceRecorderComponent(root);
  }
};

document.addEventListener('DOMContentLoaded', mountApp);

