#!/usr/bin/perl -w

#-----------------------------------------------------------------------
# Sample program using a Microsoft MSSQL database
#
# The example here uses a hardcoded username/password when connecting
# to a MSSQL database and fetching some information.
# 
# Anybody with access to this script will know the password. 
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

use strict;
use DBI;

#my $db_host= "appserver.wmware.pam.local";
my $db_host= "localhost";
my $db_database= 'hr';
my $db_userid= "hr6423150";
my $db_passwd= "wwdkUK2oTDW4CkiSrgAgXEwf";

my $dsn = "driver={ODBC Driver 17 for SQL Server};Server=".$db_host.";database=".$db_database;
my $dbh = DBI->connect( "dbi:ODBC:$dsn", $db_userid, $db_passwd, 
			{RaiseError => 1, AutoCommit => 1}) 
			|| die "Database connection not made: $DBI::errstr";

my $sql = "select top 10 employee_id, first_name, last_name, phone_number, email from dbo.employees";
my $sth = $dbh->prepare( $sql );

$sth->execute();

my( $id, $first_name, $last_name, $phone_number, $email );
$sth->bind_columns( undef, \$id, \$first_name, \$last_name, \$phone_number, \$email);

while( $sth->fetch() ) {
	if (!defined $first_name) {$first_name= "<first_name n/a>";}
	if (!defined $last_name) {$last_name= "<last_name n/a>";}
	if (!defined $phone_number) {$phone_number= "<phone_number n/a>";}
	if (!defined $email) {$email= "<email n/a>";}
	if (!defined $id) {$id= "<id n/a>";}
	print "$id, $first_name, $last_name, $phone_number, $email\n";
}

$sth->finish();
$dbh->disconnect;

# --- end of script ---