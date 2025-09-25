#!/usr/bin/perl -w

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



use strict;
use DBI;
use XML::Simple;
$XML::Simple::PREFERRED_PARSER= 'XML::Parser';

#----------------------------------------------------------------
my $alias= "mssql-hr";
my $dbh= mssqlConnect($alias);

if (defined $dbh) {
	my $sql = "select top 10 employee_id, first_name, last_name, phone_number, email from dbo.employees";
	my $sth = $dbh->prepare( $sql );
	
	$sth->execute();
	if (defined $DBI::errstr) {
		print "mssqlQuery: \$DBI::errstr= $DBI::errstr\n" ;
	}
	else {
		my( $id, $first_name, $last_name, $phone_number, $email );
		$sth->bind_columns( undef, \$id, \$first_name, \$last_name, \$phone_number, \$email);

		while( $sth->fetch() ) {
			if (!defined $id) {$id= "<id>";}
			if (!defined $first_name) {$first_name= "<first_name>";}
			if (!defined $last_name) {$last_name= "<last_name>";}
			if (!defined $phone_number) {$phone_number= "<phone_number>";}
			if (!defined $email) {$email= "<email>";}
			print "$id, $first_name, $last_name, $phone_number, $email\n";
		}

		$sth->finish();
		$dbh->disconnect;
	}
} 
else {
	print "No connection to database. \$DBI::errstr= $DBI::errstr\n";
}


# ----------------------------------------------------------------
# mssqlConnect is used to establish a database connection 
# an MSSQL database
#
# If there is a connection failure using cached credentials
# a query is made directly to PAM by passing the cache.
# 
# parameter
#     alias    - the account alias
#
# return 
#     dbh      - handle to DBI::Connect 
#                undef if the connection could not be made
# ----------------------------------------------------------------
sub mssqlConnect {
	my $alias= shift;
	my $dbh;

	# Fetch the password from local cache if available.
	my ($rc, $db_hostname, $db_port, $db_database, $db_userid, $db_passwd)= mssqlGetCredentials($alias,"false");

	if ($rc=="400") {
		print "mssqlConnect: bypassCache=false - alias=$alias, hostname=$db_hostname, userid=$db_userid, passwd=$db_passwd\n";

		my $dsn = "driver={ODBC Driver 17 for SQL Server};Server=".$db_hostname;
		if (ref($db_database) ne "HASH") {$dsn.= ";database=".$db_database;}

		$dbh = DBI->connect( "dbi:ODBC:$dsn", $db_userid, $db_passwd, {PrintError => 0, RaiseError => 0, AutoCommit => 1});
		if (!$dbh) {
			# Connect failed. Fetch the password directly from server.  
			# If there is a new password, try to connect again
			my $db_passwd2;
			($rc, $db_hostname, $db_port, $db_database, $db_userid, $db_passwd2)= mssqlGetCredentials($alias,"true");
			print "mssqlConnect: bypassCache=true - alias=$alias, hostname=$db_hostname, userid=$db_userid, passwd2=$db_passwd2\n";

			if ($rc=="400") {
				if ($db_passwd ne $db_passwd2) {
					$dsn = "driver={ODBC Driver 17 for SQL Server};Server=".$db_hostname;
					if (ref($db_database) ne "HASH") {$dsn.= ";database=".$db_database;}
					$dbh = DBI->connect( "dbi:ODBC:$dsn", $db_userid, $db_passwd2, {PrintError => 0, RaiseError => 0, AutoCommit => 1});
				}
				else {
					print "mssqlConnect: Other error, password in cache and PAM are identical\n";
				}
			}
		}
	}

	if ($rc!="400") {
		print "mssqlConnect: Could not retrieve the password for alias=$alias, error code= $rc\n";
	} else {
		if (!$dbh) {
			print "mssqlConnect: Database connection not available: $DBI::errstr";
		}
	}

	return $dbh;
}

# ----------------------------------------------------------------
# mssqlGetCredentials is used to fetch connection details from PAM
#
# The function will call getCredentials fetching everything found 
# for the alias used. If the type is "mssql" in the result
# from A2A, it is parsed and connection details for MSSQL is 
# returned.
#
# parameter
#     alias    - the account alias
#
# return 
#     array with connection details
#       res[0] - return code from PAM
#       res[1] - hostname 
#       res[2] - port
#       res[3] - database (or HASH if unknown)
#       res[4] - username
#       res[5] - password 
# ----------------------------------------------------------------
sub mssqlGetCredentials {
	my $alias= shift;
	my $bypassCache= shift;
	
	my $answer = getCredentials($alias, $bypassCache);
	my $xml = new XML::Simple;
	my $res= $xml->XMLin($answer);

	my $rc= $res->{'errorcode'};

	my @result= ($rc);
	if ($rc == "400") {
		if ($res->{'credential'}->{'TargetApplication'}->{'extensionType'} eq "mssql") {
			my $tmp;
			if ($tmp=$res->{'credential'}->{'TargetServer'}->{'hostName'}) {push(@result, $tmp);}
			if ($tmp=$res->{'credential'}->{'TargetApplication'}->{'Attribute.port'}) {push(@result, $tmp);}
			if ($tmp=$res->{'credential'}->{'TargetApplication'}->{'Attribute.instance'}) {push(@result, $tmp);}
			if ($tmp=$res->{'credential'}->{'TargetAccount'}->{'userName'}) {push(@result, $tmp);}
			if ($tmp=$res->{'credential'}->{'TargetAccount'}->{'password'}) {push(@result, $tmp);}
		} else {
			$result[0]= "415";	# extensionType unsupported
			push(@result, $res->{'credential'}->{'TargetApplication'}->{'extensionType'});
		}
	}
	return @result;
}

# ----------------------------------------------------------------
# getCredentials is used to fetch password and more from PAM
# using the A2A client. Information returned is everything
# found for an alias. It can be any application type
# 
# parameter
#     alias    - the account alias
#
# return 
#     XML structure
# ----------------------------------------------------------------
sub getCredentials {
	my $alias= shift;
	my $bypassCache= shift;
	
	my $cmd= "";
	$ENV{'CSPM_CLIENT_HOME'} = "c:/cspm/cloakware" if (!$ENV{'CSPM_CLIENT_HOME'});
	if (4 == (length pack 'P', -1)) {
		$ENV{'CSPM_CLIENT_BIT_TYPE'} = "32" if (!$ENV{'CSPM_CLIENT_BIT_TYPE'});
		$cmd= $ENV{'CSPM_CLIENT_HOME'}."/cspmclient/bin/cspmclient " . $alias . " " . $bypassCache . " -x";
	
	} elsif (8 == (length pack 'P', -1)) {
		$ENV{'CSPM_CLIENT_BIT_TYPE'} = "64" if (!$ENV{'CSPM_CLIENT_BIT_TYPE'});
		$cmd= $ENV{'CSPM_CLIENT_HOME'}."/cspmclient/bin64/cspmclient64 " . $alias . " " . $bypassCache . " -x";
	}
	
	my $output= "";
	open CSPM, '-|', $cmd or die "Could not run '$cmd'\n";
	while (my $l = <CSPM>) { $output .= $l; }
	close CSPM;
	
	#print "getCredentials: \$output= $output\n";
	return $output;
}

# --- end of script ---