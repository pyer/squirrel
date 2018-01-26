
var app = angular.module('squirrelApp', []);
app.controller('squirrelCtrl', function($scope,$http) {
    $scope.name    = "Squirrel";
    $scope.version = "${project.version}";
    $scope.jobsList = [
        "Alfreds Futterkiste",
        "Berglunds snabbköp",
        "Centro comercial Moctezuma",
        "Ernst Handel",
        ];
    $http.get("/data/projects")
            .then(function(response) {
        $scope.projects = response.data.split('\n');
    });

});

