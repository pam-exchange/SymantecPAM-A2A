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
            dbh = pyodbc.connect(dsn, user=db_userid, password=db_passwd, autocommit=True)
        except pyodbc.Error:
            # Connection failed, try fetching directly from server
            print("mssqlConnect: Connection with cached credentials failed. Fetching from server.")
            creds2 = mssql_get_credentials(alias, "true")
            rc2, db_hostname2, _, _, _, db_passwd2 = creds2 + (None,) * (6 - len(creds2))
            print(f"mssqlConnect: bypassCache=true - alias={alias}, hostname={db_hostname2}, userid={db_userid}, passwd2={db_passwd2}")

            if rc2 == "400":
                if db_passwd != db_passwd2:
                    try:
                        dbh = pyodbc.connect(dsn, user=db_userid, password=db_passwd2, autocommit=True)
                    except pyodbc.Error as ex:
                         print(f"mssqlConnect: Database connection not available: {ex}")
                else:
                    print("mssqlConnect: Other error, password in cache and PAM are identical")
    
    if rc != "400":
        print(f"mssqlConnect: Could not retrieve the password for alias={alias}, error code= {rc}")
    elif not dbh:
        print("mssqlConnect: Database connection not available.")
        
    return dbh

def main():
    alias = "mssql-hr"
    dbh = mssql_connect(alias)

    if dbh:
        try:
            with dbh.cursor() as cursor:
                sql = "SELECT TOP 10 employee_id, first_name, last_name, phone_number, email FROM dbo.employees"
                cursor.execute(sql)
                
                for row in cursor.fetchall():
                    emp_id, first_name, last_name, phone, email = row
                    print(f"{emp_id or '<id>'}, {first_name or '<first_name>'}, {last_name or '<last_name>'}, {phone or '<phone_number>'}, {email or '<email>'}")
        except pyodbc.Error as e:
            print(f"mssqlQuery: Error executing query: {e}")
        finally:
            dbh.close()
    else:
        print("No connection to database.")

if __name__ == "__main__":
    main()
    print("\n--- end of script ---")