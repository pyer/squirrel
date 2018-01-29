
var app = angular.module('squirrelApp', ["ngRoute"]);

app.config(function($routeProvider) {
    $routeProvider
    .when("/projects", {
        templateUrl : "projects.html",
        controller : "projectsCtrl"
    })
    .when("/settings", {
        templateUrl : "settings.html"
    })
    .when("/people", {
      template : "<div id=\"projects\">People</div>"
    })
    .when("/builds", {
        template : "<div id=\"projects\">Builds</div>"
    })
    .otherwise({
        template : "<div>Not found</div>"
    });

});

app.controller('projectsCtrl', function($scope,$http) {
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
