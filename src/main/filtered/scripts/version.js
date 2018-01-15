var app = angular.module("squirrelApp", []);
app.controller("squirrelCtrl", function($scope) {
  console.log("Versions");
  $scope.engineName      = "NanoHTTPD";
  $scope.engineVersion   = "version 2.2.0";
  $scope.squirrelName    = "${project.name}";
  $scope.squirrelVersion = "${project.version}";
});
