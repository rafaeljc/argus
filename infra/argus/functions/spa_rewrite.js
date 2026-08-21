// Viewer-request function for a single-page application.
//
// The bundle is a set of real files plus one entry point. A request for
// /assets/index-a1b2c3.js names a file that exists; a request for /portfolio
// names a client-side route that does not. Without this, the second returns an
// error from S3 instead of loading the application that knows how to render it.
//
// A dot in the last path segment is the signal that a real file was asked for,
// which leaves genuinely missing assets to fail as they should.
//
// Runs on the CloudFront Functions runtime: no const, no let, no arrow
// functions.
function handler(event) {
    var request = event.request;
    var segments = request.uri.split('/');
    var last = segments[segments.length - 1];

    if (last.indexOf('.') === -1) {
        request.uri = '/index.html';
    }

    return request;
}
