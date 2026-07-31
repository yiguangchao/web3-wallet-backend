variable "project_id" { type = string }
variable "region" { type = string }

resource "google_service_account" "signer" {
  account_id   = "wallet-signer"
  display_name = "Wallet isolated transaction signer"
}

resource "google_kms_key_ring" "wallet" {
  name     = "wallet-signing"
  location = var.region
}

resource "google_kms_crypto_key" "withdraw" {
  name            = "withdraw-hot-wallet"
  key_ring        = google_kms_key_ring.wallet.id
  purpose         = "ASYMMETRIC_SIGN"
  rotation_period = null
  version_template {
    algorithm        = "EC_SIGN_SECP256K1_SHA256"
    protection_level = "HSM"
  }
  lifecycle { prevent_destroy = true }
}

resource "google_kms_crypto_key_iam_member" "sign" {
  crypto_key_id = google_kms_crypto_key.withdraw.id
  role          = "roles/cloudkms.signerVerifier"
  member        = "serviceAccount:${google_service_account.signer.email}"
}

resource "google_service_account_iam_member" "workload_identity" {
  service_account_id = google_service_account.signer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[default/wallet-signer]"
}

