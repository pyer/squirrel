// Squirrel application
// --------------------

// Name and version
const header = new Vue({
  el: '#header-left',
  data: {
    name: 'Squirrel',
    version: ''
  },
  created() {
    fetch('/version').then(response => response.json())
                     .then(response => this.version = response.version);
    }
})

// Router

// 1. Define route components.
const Projects = {
  template: '<div>{{list}}</div>',
  data: function() {
    list: 'vide'
//    console.log("fetch projects");
//    fetch('/projects').then(response => response.json()).then(data => result = data.projects);
//    fetch('/projects').then(response => response.json())
//                     .then(response => this.list = "response.projects");
//    fetch('/projects').then(response => response.json())
//                         .then(data => projects = data.projects);
//    console.log(projects);
    return { list: "projects" }
  }
}


const Bar = { template: '<div>bar</div>' }

//
//



// 2. Define some routes
// Each route should map to a component. The "component" can
// either be an actual component constructor created via
// Vue.extend(), or just a component options object.
// We'll talk about nested routes later.

// 3. Create the router instance and pass the `routes` option
// You can pass in additional options here, but let's
// keep it simple for now.
const router = new VueRouter({
  routes: [
    { path: '/projects', component: Projects },
    { path: '/settings', component: Bar },
    { path: '/people',   component: Bar },
    { path: '/builds',   component: Bar }
  ]
})

// 4. Create and mount the root instance.
// Make sure to inject the router with the router option to make the
// whole app router-aware.
const app = new Vue({
  router
}).$mount('#app')

// Now the app has started!
