import ssl
from http.server import HTTPServer, BaseHTTPRequestHandler
import logging
from log_config import setup_logging
from pkcs12_loader import load_pkcs12_key_cert
import request_handlers
import hashlib
import os
import subprocess
import shlex

logger = logging.getLogger(__name__)

keystore_alias= "Python-WebServer"
keystore_filename= "Keystore/Python-WebServer.keystore"
filename_list= "filelist.sha256"
filelist_hash= "40ddad4f358bd8488583f6e4c7e258b6e72646061450db6dcafd9d6ed61f5594"
comm_server= "appserver.vmware.pam.local"
comm_port= 7443

class CustomHTTPRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        logger.debug(f"Received GET request for path: {self.path}")
        if self.path == '/hello':
            request_handlers.handle_hello(self)
        elif self.path == '/favicon.ico':
            request_handlers.handle_favicon(self)
        else:
            # Serve any static file under /static/filename.ext
            if self.path.startswith('/static/'):
                rel_path = self.path[len('/static/'):]
                request_handlers.handle_static_file(self, rel_path)
            else:
                self.send_error(404, "Not Found")

    def log_message(self, format, *args):
        logger.info("%s - - [%s] %s" % (
            self.client_address[0],
            self.log_date_time_string(),
            format % args
        ))

def run_server():

    """ 
    Change the alias to match registration in PAM
    The password is required to unlock the keystore where the private 
    key for HTTPS is stored. Failing fetching the correct password
    will give an error when starting the HTTPS server.
    """
    password = get_keystore_password( keystore_alias )
    p12_file = keystore_filename

    # Load key/cert from PKCS12
    private_key, certificate, additional_certs = load_pkcs12_key_cert(p12_file, password)

    # Write key/cert to temporary PEM files for use with Python's ssl module
    import tempfile
    from cryptography.hazmat.primitives import serialization

    with tempfile.NamedTemporaryFile(delete=False) as key_file, \
         tempfile.NamedTemporaryFile(delete=False) as cert_file:
        key_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption()
        )
        cert_pem = certificate.public_bytes(serialization.Encoding.PEM)
        key_file.write(key_pem)
        cert_file.write(cert_pem)
        key_path, cert_path = key_file.name, cert_file.name

    server_address = (comm_server, comm_port)
    httpd = HTTPServer(server_address, CustomHTTPRequestHandler)
    logger.info(f"Starting HTTPS server on https://{server_address[0]}:{server_address[1]}")

    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certfile=cert_path, keyfile=key_path)
    httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Server shutdown requested by user")
    finally:
        import os
        os.unlink(key_path)
        os.unlink(cert_path)
        logger.info("Temporary key and cert files deleted")


def get_keystore_password(alias):
    # Stub: Replace with secure password prompt/retrieval logic
    logger.debug(f"Retrieval password for alias '{alias}'")
    
    rsp= a2a_get_credential(alias)
    if rsp[0] == "400":
        return rsp[2]
    print(f"A2A client - statusCode= {rsp[0]}")
    return "*** Not available ***"

    
def a2a_get_credential(alias, bypassCache=False, displayXml=False):
    
    """
    Call the A2A client program 'cspmclient' when fetching username, password
    for an alias. The script is written in Python and in PAM the hash of this
    source file is the registered script. Changes to this file will generate 
    a new integrity value and with correct definition in PAM, a password
    is only released if this source file is unchanged.
    """
    cspmClientHome = os.getenv('CSPM_CLIENT_HOME')
    if cspmClientHome == None:
        cspmClientHome="c:\\cspm\\cloakware"

    cmd = cspmClientHome+"/cspmclient/bin/cspmclient" +" "+ alias+" "+str(bypassCache)+" "+str(displayXml)
    cmd.replace("\\","/")
    
    if os.name == 'nt':
        args = shlex.split(cmd, posix=False)
    else:
        args = shlex.split(cmd)

    childprocess = subprocess.Popen(args, stdout=subprocess.PIPE)
    rsp= childprocess.communicate()[0]
    
    rsp= str(rsp, "utf-8")
    rsp= rsp.strip().split(" ")
    return rsp
    

def a2a_verify_file(filename, expected_hash):

    """
    Verify hash of one (1) file.
    :param filename:
    :param expected_hash:
    :return: True: calculated hash matches expected_hash
             False: otherwise
    """

    try:
        
        sha256_hash = hashlib.sha256()
        with open(filename, 'rb') as fileopen:
            while True:
                file_buf= fileopen.read(1024000)
                if not file_buf:
                    break
                sha256_hash.update(file_buf)

        actual_hash= sha256_hash.hexdigest()
        actual_hash= actual_hash.lower()
        expected_hash= expected_hash.lower()

        if expected_hash == actual_hash:
            # logger.trace(f"filename: {filename} - hash {actual_hash}")
            return True
        else:
            logger.warning(f"filename: {filename}")
            logger.warning(f"expected_hash: '{expected_hash}'")
            logger.warning(f"actual hash: '{actual_hash}'")
            return False

    except Exception as err:
        logger.error(f"Exception filename: {filename}")
        return False


def a2a_verify_filelist(filename_list, filehash_list):

    """
    Verify hashes of the files in the filelist
    The filelist contains a list of files and hashes of each file.
    :param filename_list:
    :param filehash_list:
    :return: True: Hash of filelist (combined) and hash of every file has been recalculated. 
                   If they all match files have not been tampered with.
             False: otherwise.
    """

    try:

        logger.info(f"Validating integrity of filelist '{filename_list}'")
        
        # first verify the hash of the filelist file
        if not a2a_verify_file(filename_list, filehash_list):
            logger.error(f"Integrity of '{filename_list}': Not OK")
            return False

        # now read hashes and filenames from filelist and verify each of them
        with open(filename_list, 'r') as file_open:
            while True:
                line= file_open.readline().strip()
                if not line:
                    break

                filehash,filename = line.split(" *")
                filehash= filehash.lower()
                if not a2a_verify_file(filename, filehash):
                    return False

        return True

    except Exception as err:
        logger.error(f"{err}")
        return False


if __name__ == "__main__":

    setup_logging()

    """
    Before starting the HTTPS server the integrity 
    of all files in a filelist is checked.
    The expected hash value of the filelist is entered directly
    in the variable filelist_hash.
    There is a Powershell script 'hash-filelist.ps1' which is 
    used to generate hash of individual files in a filelist 
    and a hash of the combined filelist including hashes of files.
    """
    fileCheck= a2a_verify_filelist(filename_list,filelist_hash)
    if not fileCheck:
        logger.error("Cannot verify integrity of files")
        quit()

    run_server()
    
