#!/usr/bin/perl -w

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

use strict;
use DBI;
use XML::Simple;
$XML::Simple::PREFERRED_PARSER= 'XML::Parser';

my $alias= "mssql-hr";
my $answer = getCredentials($alias,"false");
my $xml = new XML::Simple;
my $res= $xml->XMLin($answer);

my $rc= $res->{'errorcode'};
if ($rc != "400") {
	print "Return code= $rc\n";
} else {
	
	my $db_hostname= $res->{'credential'}->{'TargetServer'}->{'hostName'};
	my $db_port= $res->{'credential'}->{'TargetApplication'}->{'Attribute.port'};
	my $db_database= $res->{'credential'}->{'TargetApplication'}->{'Attribute.instance'};
	my $db_userid= $res->{'credential'}->{'TargetAccount'}->{'userName'};
	my $db_passwd= $res->{'credential'}->{'TargetAccount'}->{'password'};

	print "alias=$alias, hostname=$db_hostname, port= $db_port, userid=$db_userid, passwd=$db_passwd\n";

	my $dsn = "driver={ODBC Driver 17 for SQL Server};Server=".$db_hostname;
	if (ref($db_database) ne "HASH") {$dsn.= ";database=".$db_database;}
	
	print "dsn= ".$dsn."\n";
	
	my $dbh = DBI->connect( "dbi:ODBC:$dsn", $db_userid, $db_passwd, 
				{RaiseError => 1, AutoCommit => 1}) 
				|| die "Database connection not made: $DBI::errstr";

	my $sql = "select top 10 employee_id, first_name, last_name, phone_number, email from dbo.employees";
	my $sth = $dbh->prepare( $sql );

	$sth->execute();

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
	
	return $output;
}

# --- end of script ---