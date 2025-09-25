#-----------------------------------------------------------------------
# Sample program using a Microsoft MSSQL database
#
# First approach to let PAM manage the password for the database account.
# It uses A2A when fetching credentials. Teh credentials are returned
# as an XML structure. From there the hostname, port, database, etc.
# is fetched when opening a connection to the database.
#
# Username/password is no longer in the script.
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

    cmd = [client_path, alias, bypass_cache, "-x"]
    
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return result.stdout
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"Could not run '{' '.join(cmd)}': {e}")
        return None

def main():
    alias = "mssql-hr"
    answer = get_credentials(alias, "false")

    if not answer:
        return

    try:
        root = ET.fromstring(answer)
        
        error_code = root.findtext("errorcode")
        if error_code != "400":
            print(f"Return code = {error_code}")
            return

        db_hostname = root.findtext("credential/TargetServer/hostName")
        db_port = root.findtext("credential/TargetApplication/Attribute.port")
        db_database = root.findtext("credential/TargetApplication/Attribute.instance")
        db_userid = root.findtext("credential/TargetAccount/userName")
        db_passwd = root.findtext("credential/TargetAccount/password")

        print(f"alias={alias}, hostname={db_hostname}, port={db_port}, userid={db_userid}, passwd={db_passwd}")

        dsn = f"DRIVER={{ODBC Driver 17 for SQL Server}};SERVER={db_hostname}"
        if db_database:
            dsn += f";DATABASE={db_database}"

        print(f"dsn= {dsn}")

        try:
            with pyodbc.connect(dsn, user=db_userid, password=db_passwd) as dbh:
                cursor = dbh.cursor()
                sql = "SELECT TOP 10 employee_id, first_name, last_name, phone_number, email FROM dbo.employees"
                cursor.execute(sql)

                for row in cursor.fetchall():
                    employee_id, first_name, last_name, phone_number, email = row
                    
                    employee_id = employee_id if employee_id is not None else "<id>"
                    first_name = first_name if first_name is not None else "<first_name>"
                    last_name = last_name if last_name is not None else "<last_name>"
                    phone_number = phone_number if phone_number is not None else "<phone_number>"
                    email = email if email is not None else "<email>"

                    print(f"{employee_id}, {first_name}, {last_name}, {phone_number}, {email}")

        except pyodbc.Error as ex:
            sqlstate = ex.args[0]
            print(f"Database connection not made: {sqlstate}")

    except ET.ParseError as e:
        print(f"Failed to parse XML: {e}")

if __name__ == "__main__":
    main()
    print("\n--- end of script ---")
    