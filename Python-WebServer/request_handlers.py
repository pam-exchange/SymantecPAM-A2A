import os
import logging

logger = logging.getLogger(__name__)

CONTENT_DIR = os.path.join(os.path.dirname(__file__), 'content')

def handle_hello(handler):
    logger.debug("Handling /hello GET request")
    file_path = os.path.join(CONTENT_DIR, 'hello.html')
    if not os.path.exists(file_path):
        logger.warning("hello.html not found")
        handler.send_error(404, "File Not Found")
        return
    with open(file_path, 'rb') as f:
        content = f.read()
    handler.send_response(200)
    handler.send_header("Content-Type", "text/html")
    handler.send_header("Content-Length", str(len(content)))
    handler.end_headers()
    handler.wfile.write(content)
    logger.info("Served hello.html successfully")

def handle_favicon(handler):
    logger.debug("Handling /favicon.ico GET request")
    file_path = os.path.join(CONTENT_DIR, 'favicon.ico')
    if not os.path.exists(file_path):
        logger.warning("favicon.ico not found")
        handler.send_error(404, "File Not Found")
        return
    with open(file_path, 'rb') as f:
        content = f.read()
    handler.send_response(200)
    handler.send_header("Content-Type", "text/html")
    handler.send_header("Content-Length", str(len(content)))
    handler.end_headers()
    handler.wfile.write(content)
    logger.info("Served favicon.ico successfully")

def handle_static_file(handler, rel_path):
    logger.debug(f"Handling static file request: {rel_path}")
    safe_path = os.path.normpath(os.path.join(CONTENT_DIR, rel_path.lstrip('/')))
    if not safe_path.startswith(CONTENT_DIR) or not os.path.exists(safe_path):
        logger.warning("Requested static file not found or outside content directory")
        handler.send_error(404, "File Not Found")
        return
    with open(safe_path, 'rb') as f:
        content = f.read()
    handler.send_response(200)
    handler.send_header("Content-Type", "application/octet-stream")
    handler.send_header("Content-Length", str(len(content)))
    handler.end_headers()
    handler.wfile.write(content)
    logger.info(f"Served static file: {safe_path}")