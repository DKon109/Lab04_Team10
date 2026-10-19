param([string]$BaseUrl = 'http://localhost:5174')
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('localhost', '127.0.0.1')) { throw 'This script creates demo records and only runs against localhost.' }

function Invoke-DemoApi($Path, $Body, $Token = $null, $Method = 'POST') {
    $parameters = @{ Uri = "$BaseUrl/api$Path"; Method = $Method; ContentType = 'application/json' }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    $response = Invoke-RestMethod @parameters
    if ($response -is [array]) { foreach ($item in $response) { Write-Output $item } }
    else { $response }
}
function Get-DemoStudent($Email) {
    $credentials = @{ email = $Email; password = 'TixoraDemo3!' }
    try { $null = Invoke-DemoApi '/auth/register' $credentials }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 409) { throw } }
    (Invoke-DemoApi '/auth/login' $credentials).token
}
function Assert-Demo($Condition, $Message) { if (-not $Condition) { throw $Message } }

$organiser = Invoke-DemoApi '/auth/login' @{email='committee.demo@uni.sydney.edu.au';password='OrganiserDemo1!'}
$student = Get-DemoStudent 'sprint3.attendee@uni.sydney.edu.au'
$friend = Get-DemoStudent 'sprint3.friend@uni.sydney.edu.au'
$event = Invoke-DemoApi '/events' @{
    title='Sprint 3 Campus Film Night (demo)'; description='Synthetic demo event for the Sprint 3 booking walkthrough. Venue labels are demo fixtures, not a verified campus map.'
    dateTime=(Get-Date).AddDays(14).ToString('yyyy-MM-ddTHH:mm:ss'); venue='Demo room - temporary seat labels A1-A12'; city='Sydney'; university='USYD'
    organiserName='USYD Coding Society'; organiserType='SOCIETY'; category='Arts'; tags=@('demo','film','friends'); capacity=12; seatIdentifiersConfirmed=$true
} $organiser.token
$null = Invoke-DemoApi "/organiser/events/$($event.id)/seating" @{
    source='EXTERNAL'; template='FRONT'; rows=3; seatsPerRow=4; aisleAfter=2; blockedCodes=@('B4'); accessibleCodes=@('A1'); identifiersConfirmed=$true
} $organiser.token 'PUT'
$null = Invoke-DemoApi "/events/$($event.id)/status" @{status='published'} $organiser.token 'PATCH'
$path = "/events/$($event.id)/mock-checkout"
foreach ($outcome in @('DECLINED', 'CANCELLED')) {
    $attempt = Invoke-DemoApi $path @{idempotencyKey=[guid]::NewGuid().ToString();outcome=$outcome} $student
    Assert-Demo ($null -eq $attempt.booking) "$outcome unexpectedly created a booking"
}
$request = @{idempotencyKey=[guid]::NewGuid().ToString();outcome='SUCCESS';createGroup=$true;accessibilityRequired=$true}
$confirmed = Invoke-DemoApi $path $request $student
$retry = Invoke-DemoApi $path $request $student
Assert-Demo ($confirmed.booking.id -eq $retry.booking.id) 'Retry created a different booking'
$joined = Invoke-DemoApi $path @{idempotencyKey=[guid]::NewGuid().ToString();outcome='SUCCESS';joinGroupCode=$confirmed.booking.groupCode.ToLower()} $friend
Assert-Demo ($joined.booking.groupCode -eq $confirmed.booking.groupCode) 'Friend did not join the same group'
$mine = @(Invoke-DemoApi '/auth/me/bookings' $null $student 'GET')
Assert-Demo (@($mine | Where-Object eventId -eq $event.id).Count -eq 1) 'Private booking list did not contain exactly one booking'
$attendees = @(Invoke-DemoApi "/events/$($event.id)/bookings" $null $organiser.token 'GET')
Assert-Demo ($attendees.Count -eq 2) 'Expected exactly two attendees'
$null = Invoke-DemoApi "/events/$($event.id)/status" @{status='closed'} $organiser.token 'PATCH'
$allocation = Invoke-DemoApi "/organiser/events/$($event.id)/allocate" $null $organiser.token
$repeated = Invoke-DemoApi "/organiser/events/$($event.id)/allocate" $null $organiser.token
Assert-Demo ($allocation.completedAt -eq $repeated.completedAt) 'Allocation retry created a different run'
$ticket = Invoke-DemoApi "/bookings/$($confirmed.booking.id)/ticket" $null $student 'GET'
Assert-Demo ($ticket.seatCode -eq 'A1') 'Accessible attendee did not receive A1'
try {
    $null = Invoke-DemoApi "/bookings/$($confirmed.booking.id)/ticket" $null $friend 'GET'
    throw 'Another attendee could read the private ticket'
} catch { if ([int]$_.Exception.Response.StatusCode -ne 404) { throw } }
$csv = Invoke-DemoApi "/organiser/events/$($event.id)/allocation/export" $null $organiser.token 'GET'
Assert-Demo ($csv -notmatch '@uni.sydney.edu.au') 'CSV exposed attendee emails'
Assert-Demo ($csv -match $confirmed.booking.bookingRef) 'CSV missing booking reference'
Write-Output "PASS: draft/layout/publish, decline/cancel/success, safe retries, private group, close/allocate, private seat ticket and anonymous CSV."
Write-Output "Demo event ID: $($event.id); group: $($confirmed.booking.groupCode); booking: $($confirmed.booking.bookingRef)"
Write-Output "Ticket URL: $BaseUrl/bookings/$($confirmed.booking.id)/ticket; allocation processing: $($allocation.elapsedMillis) ms"
Write-Output 'Attendee: sprint3.attendee@uni.sydney.edu.au / TixoraDemo3!'
Write-Output 'Friend: sprint3.friend@uni.sydney.edu.au / TixoraDemo3!'
