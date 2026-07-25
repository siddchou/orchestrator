export interface Credential {
  id: number;
  ref: string;
  type: 'PASSWORD' | 'SSH_KEY';
  createdAt?: string;
}

export interface CredentialFormData {
  ref: string;
  type: 'PASSWORD' | 'SSH_KEY';
  value: string;
}

export interface KeyGenerationRequest {
  ref: string;
  algorithm: 'RSA' | 'ED25519';
  rsaKeySize?: 2048 | 4096;
}

export interface KeyGenerationResponse {
  privateKey: string;
  publicKey: string;
  fingerprint: string;
  algorithm: string;
}
