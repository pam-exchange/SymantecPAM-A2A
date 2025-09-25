#-----------------------------------------------------------------------
# Sample program using a Microsoft MSSQL database
#
# Optimal approach is to setup A2A account using the A2A client caching 
# mechanism. With this the A2A client will only query PAM when needed.
# Alas, most of the time the credentials are found in A2A cache
# and they are returned very quickly (~ 200mS) instead of a roundtrip to 
# PAM (~3 seconds}. Drawback is that the application must take into 
# account that the credentials returned from cache may be obsolete.
#
# The example here is handling a scenario where the password retrieved
# from A2A client is invalid and make a second query but forcing
# the request to PAM instead of using cache.
#
#-----------------------------------------------------------------------

#*********************************************************************
# MIT License
#
# Copyright (c) 2024-2025 PAM-Exchange
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#**********************************************************************

import os
import platform
import subprocess
import xml.etree.ElementTree as ET
import pyodbc

def get_credentials(alias, bypass_cache):
    """
    Fetches credentials from PAM using the A2A client.
    """
    cspm_client_home = os.environ.get("CSPM_CLIENT_HOME", "c:/cspm/cloakware")

    if platform.architecture()[0] == "32bit":
        client_path = os.path.join(cspm_client_home, "cspmclient", "bin", "cspmclient")
    else:
        client_path = os.path.join(cspm_client_home, "cspmclient", "bin64", "cspmclient64")

    cmd = [client_path, alias, str(bypass_cache).lower(), "-x"]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return result.stdout
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"Could not run '{' '.join(cmd)}': {e}")
        return None

def mssql_get_credentials(alias, bypass_cache):
    """
    Fetches and parses MSSQL connection details from PAM.
    """
    answer = get_credentials(alias, bypass_cache)
    if not answer:
        return ("-1",)

    try:
        root = ET.fromstring(answer)
        rc = root.findtext("errorcode")
        result = [rc]
        if rc == "400":
            if root.findtext("credential/TargetApplication/extensionType") == "mssql":
                result.append(root.findtext("credential/TargetServer/hostName"))
                result.append(root.findtext("credential/TargetApplication/Attribute.port"))
                result.append(root.findtext("credential/TargetApplication/Attribute.instance"))
                result.append(root.findtext("credential/TargetAccount/userName"))
                result.append(root.findtext("credential/TargetAccount/password"))
            else:
                result[0] = "415"  # Unsupported extensionType
                result.append(root.findtext("credential/TargetApplication/extensionType"))
        return tuple(result)
    except ET.ParseError:
        return ("-1",)

def mssql_connect(alias):
    """
    Establishes a database connection to an MSSQL database, with retry logic.
    """
    dbh = None

    # Fetch from cache first
    creds = mssql_get_credentials(alias, "false")
    rc, db_hostname, db_port, db_database, db_userid, db_passwd = creds + (None,) * (6 - len(creds))

    if rc == "400":
        print(f"mssqlConnect: bypassCache=false - alias={alias}, hostname={db_hostname}, userid={db_userid}, passwd={db_passwd}")
        dsn = f"DRIVER={{ODBC Driver 17 for SQL Server}};SERVER={db_hostname}"
        if db_database:
            dsn += f";DATABASE={db_database}"

        try:
            dbh = pyo