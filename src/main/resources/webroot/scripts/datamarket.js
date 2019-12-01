function build_header(table_selector,headers) {
    var head = $("<thead>").append();
    var tr = $("<tr>");
    $.each(headers, function (idx, h) {
        tr.append($("<th>").append(h));
    });
    head.append(tr);
    $(table_selector+" thead").replaceWith(head);
};

 function populate_data(table_selector,url,col_order,row_per_page,lengthMenu,colDefs,footer,extra_options) {
    extra_options = (typeof extra_options !== 'undefined') ?  extra_options: null;
    var lenMenu = lengthMenu==null ? [10, 15, 50, 100] : lengthMenu;

//    var pageLen =  getDataTablePageLength(table_selector);
//    if(pageLen == null){
//         pageLen = row_per_page;
//    }

    var parameter = {
        ajax: url,
        pageLength: row_per_page,
    };

    if(col_order != null){
        parameter['order'] = col_order;
    }
    if(lenMenu != null ){
        parameter['lengthMenu'] = lenMenu;
    }

    if(colDefs != null){
        parameter['columnDefs'] = colDefs;
    }

    if(footer != null){
        parameter["footerCallback"] = footer;
    }

    if(extra_options != null){
        $.each(extra_options,function (n,v) {
           parameter[n] = v;
        });
    }
    return $(table_selector).DataTable(parameter);
/*
    $(table_selector).on( 'length.dt', function ( e, settings, len ) {
        setDataTablePageLength(table_selector,len);
    });
*/
};

function capitalizeFLetter(string) {
    return string[0].toUpperCase() +  string.slice(1);
}

$(document).ready(function () {
    $.getJSON("/api/md/initpaint", function(data){
        if(data.length > 0){
            var items = window.localStorage.getItem('md_items_per_page');
            items =items == null ? 15 : parseInt(items);

            var header = ['Ticker'];
            var cols = {'columns': [{ "data": "ticker" }]};
            for (var key in data[0]){
                if(key.localeCompare("ticker") !=0 && ! key.includes("_time") ){
                    header.push(capitalizeFLetter(key));
                    cols['columns'].push({ "data": key });
                }
            }
            cols['columns'].push({ "data": "received_time" });
            cols['columns'].push({ "data": "sent_time" });
            header.push("Rcv Time");
            header.push("Sent Time");
            build_header('#mdtable', header);
            // install item per page menu callback
            $('#mdtable').on('length.dt',function(e,settings, len){
                window.localStorage.setItem('md_items_per_page', len.toString());
            });

            populate_data(
                '#mdtable',
                function(d,callback,s){
                    callback({'data':data});
                },
                [[0, 'asc'], [1, 'asc']],
                items,
                [10, 20, 50, 100],
                null, //formatter
                null,
                cols
            );      
        }
        else{
            console.log("no market data");
        }
    });
});
