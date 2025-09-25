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

import pyodbc

# Database connection details
db_host = "localhost"
db_database = "hr"
db_userid = "hr6423150"
db_passwd = "wwdkUK2oTDW4CkiSrgAgXEwf"

# Connection string
dsn = f"DRIVER={{ODBC Driver 17 for SQL Server}};SERVER={db_host};DATABASE={db_database}"

try:
    # Establish database connection
    with pyodbc.connect(dsn, user=db_userid, password=db_passwd) as dbh:
        cursor = dbh.cursor()

        # SQL query
        sql = "SELECT TOP 10 employee_id, first_name, last_name, phone_number, email FROM dbo.employees"
        
        # Execute the query
        cursor.execute(sql)

        # Fetch and print results
        for row in cursor.fetchall():
            employee_id, first_name, last_name, phone_number, email = row
            
            # Handle null values
            employee_id = employee_id if employee_id is not None else "<id n/a>"
            first_name = first_name if first_name is not None else "<first_name n/a>"
            last_name = last_name if last_name is not None else "<last_name n/a>"
            phone_number = phone_number if phone_number is not None else "<phone_number n/a>"
            email = email if email is not None else "<email n/a>"

            print(f"{employee_id}, {first_name}, {last_name}, {phone_number}, {email}")

except pyodbc.Error as ex:
    sqlstate = ex.args[0]
    print(f"Database connection not made: {sqlstate}")

finally:
    print("\n--- end of script ---")