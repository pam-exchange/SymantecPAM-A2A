import logging
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.hazmat.backends import default_backend

logger = logging.getLogger(__name__)

def load_pkcs12_key_cert(pkcs12_path, password):
    logger.debug(f"Attempting to load PKCS#12 keystore from '{pkcs12_path}'")
    with open(pkcs12_path, 'rb') as f:
        pkcs12_data = f.read()
    try:
        private_key, certificate, additional_certs = pkcs12.load_key_and_certificates(
            pkcs12_data, password.encode(), backend=default_backend()
        )
        logger.info("Successfully loaded private key and certificate from PKCS#12 keystore")
        return private_key, certificate, additional_certs
    except Exception as e:
        logger.error(f"Failed to load PKCS#12 keystore: {e}")
        raise