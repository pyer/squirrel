var app = angular.module("squirrelApp", []);
app.controller("squirrelCtrl", function($scope) {
    $scope.jobsList = [
        "Alfreds Futterkiste",
        "Berglunds snabbköp",
        "Centro comercial Moctezuma",
        "Ernst Handel",
        ]
});
