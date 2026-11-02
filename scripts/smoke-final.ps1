param([string]$BaseUrl = 'http://localhost:5174')
# Creates labelled synthetic events only. Retains records for inspection; never deletes data.
. "$PSScriptRoot/smoke-sprint3.ps1" -BaseUrl $BaseUrl
$profiles = @(Invoke-DemoApi '/organiser/venue-templates' $null $organiser.token 'GET')
Assert-Demo ($profiles.Count -eq 3) 'Expected three campus catalogue templates'
$cases = @('FRONT', 'FAN', 'CENTRAL', 'GENERAL_ADMISSION') + @($profiles | ForEach-Object id)
foreach ($family in $cases) {
    $campus = $profiles | Where-Object id -eq $family | Select-Object -First 1
    $identified = $family -ne 'GENERAL_ADMISSION'
    $event = Invoke-DemoApi '/events' @{
        title="Final acceptance - $family (synthetic demo)"
        description='Synthetic software acceptance fixture. Physical identifiers are simulated; no claim of verified venue accessibility or installation.'
        dateTime=(Get-Date).AddDays(14).ToString('yyyy-MM-ddTHH:mm:ss')
        venue=$(if ($campus) { $campus.name } else { 'Synthetic external demo venue' })
        city='Sydney'; university=$(if ($campus) { 'USYD' } else { $null })
        organiserName='USYD Coding Society'; organiserType='SOCIETY'; category='Arts'; tags=@('final-demo'); capacity=12
    } $organiser.token
    if ($campus) {
        $map = $campus.map
        $map.identifiersConfirmed = $true
        $null = Invoke-DemoApi "/organiser/events/$($event.id)/seat-map" $map $organiser.token 'PUT'
    } else {
        $null = Invoke-DemoApi "/organiser/events/$($event.id)/seating" @{
            source='EXTERNAL'; template=$(if ($identified) { $family } else { 'FRONT' })
            rows=3; seatsPerRow=4; aisleAfter=2; blockedCodes=@('B4'); accessibleCodes=@('A1'); identifiersConfirmed=$identified
        } $organiser.token 'PUT'
    }
    $summary = Invoke-DemoApi "/organiser/events/$($event.id)/seating" $null $organiser.token 'GET'
    if ($campus) { Assert-Demo ($null -ne $summary.managedMap) 'Management summary lost saved campus map' }
    $null = Invoke-DemoApi "/events/$($event.id)/status" @{status='published'} $organiser.token 'PATCH'
    $first = Invoke-DemoApi "/events/$($event.id)/mock-checkout" @{
        idempotencyKey=[guid]::NewGuid().ToString(); outcome='SUCCESS'; createGroup=$identified
    } $student
    Assert-Demo ($first.booking.generalAdmission -eq (-not $identified)) 'Booking admission mode is incorrect'
    if ($identified) {
        $second = Invoke-DemoApi "/events/$($event.id)/mock-checkout" @{
            idempotencyKey=[guid]::NewGuid().ToString(); outcome='SUCCESS'; joinGroupCode=$first.booking.groupCode
        } $friend
    }
    $pending = Invoke-DemoApi "/bookings/$($first.booking.id)/ticket" $null $student 'GET'
    Assert-Demo ($null -eq $pending.seatCode) 'Deferred allocation assigned a seat before closure'
    $null = Invoke-DemoApi "/events/$($event.id)/status" @{status='closed'} $organiser.token 'PATCH'
    $run = Invoke-DemoApi "/organiser/events/$($event.id)/allocate" $null $organiser.token
    $ticket = Invoke-DemoApi "/bookings/$($first.booking.id)/ticket" $null $student 'GET'
    Assert-Demo ($ticket.booking.status -eq 'ALLOCATED') 'Ticket did not finalise'
    Assert-Demo ($ticket.generalAdmission -eq (-not $identified)) 'Ticket admission mode is incorrect'
    if ($identified) { Assert-Demo (-not [string]::IsNullOrWhiteSpace($ticket.seatCode)) 'Missing assigned seat' }
    else { Assert-Demo ($null -eq $ticket.seatCode) 'General admission invented a physical seat' }
    $csv = Invoke-DemoApi "/organiser/events/$($event.id)/allocation/export" $null $organiser.token 'GET'
    $print = Invoke-DemoApi "/organiser/events/$($event.id)/allocation/print" $null $organiser.token 'GET'
    Assert-Demo ($csv -notmatch '@uni.sydney.edu.au' -and $print -notmatch '@uni.sydney.edu.au') 'Outputs exposed attendee emails'
    Assert-Demo ($csv -match $first.booking.bookingRef) 'CSV lost booking reference'
    if ($identified) { Assert-Demo (-not $print.Contains($first.booking.groupCode)) 'Printable plan exposed reusable join code' }
    Write-Output "PASS $family : event $($event.id), ticket $BaseUrl/bookings/$($first.booking.id)/ticket, allocation $($run.elapsedMillis) ms"
}
Write-Output 'PASS: all three campus templates, all three external families and general admission, through real HTTP and MySQL.'
